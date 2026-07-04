package com.turkcell.billingservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.turkcell.billingservice.entity.BillCycle;
import com.turkcell.billingservice.entity.Invoice;
import com.turkcell.billingservice.entity.InvoiceLine;
import com.turkcell.billingservice.entity.PendingCharge;
import com.turkcell.billingservice.repository.BillCycleRepository;
import com.turkcell.billingservice.repository.InvoiceLineRepository;
import com.turkcell.billingservice.repository.InvoiceRepository;
import com.turkcell.billingservice.repository.PendingChargeRepository;
import com.turkcell.billingservice.saga.OutboxWriter;
import com.turkcell.commonlib.saga.ChargeInvoiceCommand;
import com.turkcell.commonlib.saga.SagaTopics;

/**
 * Bill-run fatura matematigi + dongu ilerletme + auto-pay komutu + asim kalemleri (G1).
 * (Canli e2e ile dogrulanan degerlerin regresyon guvencesi: 249.90 -> KDV %20 -> 299.88.)
 */
class BillRunServiceTest {

    private BillCycleRepository billCycleRepository;
    private InvoiceRepository invoiceRepository;
    private InvoiceLineRepository invoiceLineRepository;
    private PendingChargeRepository pendingChargeRepository;
    private OutboxWriter outbox;
    private BillRunService service;

    @BeforeEach
    void setUp() {
        billCycleRepository = mock(BillCycleRepository.class);
        invoiceRepository = mock(InvoiceRepository.class);
        invoiceLineRepository = mock(InvoiceLineRepository.class);
        pendingChargeRepository = mock(PendingChargeRepository.class);
        outbox = mock(OutboxWriter.class);
        service = new BillRunService(billCycleRepository, invoiceRepository, invoiceLineRepository,
                pendingChargeRepository, outbox);

        // JPA save id atar; unit testte taklit edilir.
        when(invoiceRepository.save(any())).thenAnswer(inv -> {
            Invoice i = inv.getArgument(0);
            i.setId(UUID.randomUUID());
            return i;
        });
        when(pendingChargeRepository.findPendingForUpdate(any())).thenReturn(List.of());
    }

    @Test
    void issuesInvoiceWithTaxAdvancesCycleAndEnqueuesAutoPay() {
        BillCycle cycle = cycle("249.90", LocalDate.of(2026, 7, 3));
        when(billCycleRepository.findDue(any(), anyInt())).thenReturn(List.of(cycle));

        service.runDueCycles();

        // Fatura: donem, KDV %20, vade +15 gun, ISSUED.
        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(invoiceCaptor.capture());
        Invoice invoice = invoiceCaptor.getValue();
        assertThat(invoice.getSubTotal()).isEqualByComparingTo("249.90");
        assertThat(invoice.getTax()).isEqualByComparingTo("49.98");
        assertThat(invoice.getGrandTotal()).isEqualByComparingTo("299.88");
        assertThat(invoice.getStatus()).isEqualTo("ISSUED");
        assertThat(invoice.getPeriodStart()).isEqualTo(LocalDate.of(2026, 6, 3));
        assertThat(invoice.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 7, 3));
        assertThat(invoice.getDueDate()).isEqualTo(LocalDate.of(2026, 7, 18));

        // Kalem: 1 x aylik ucret.
        ArgumentCaptor<InvoiceLine> lineCaptor = ArgumentCaptor.forClass(InvoiceLine.class);
        verify(invoiceLineRepository).save(lineCaptor.capture());
        assertThat(lineCaptor.getValue().getLineTotal()).isEqualByComparingTo("249.90");
        assertThat(lineCaptor.getValue().getInvoiceId()).isEqualTo(invoice.getId());

        // Auto-pay komutu: grandTotal ile payment-commands'a.
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outbox).enqueue(eq(SagaTopics.PAYMENT_COMMANDS), eq("ChargeInvoiceCommand"),
                eq(invoice.getId()), payloadCaptor.capture());
        ChargeInvoiceCommand cmd = (ChargeInvoiceCommand) payloadCaptor.getValue();
        assertThat(cmd.invoiceId()).isEqualTo(invoice.getId());
        assertThat(cmd.amount()).isEqualByComparingTo("299.88");
        assertThat(cmd.currency()).isEqualTo("TRY");

        // Dongu +1 ay ilerledi.
        assertThat(cycle.getNextRunDate()).isEqualTo(LocalDate.of(2026, 8, 3));
        verify(billCycleRepository).save(cycle);
    }

    @Test
    void addsPendingOverageAsTypedLinesAndMarksBilled() {
        BillCycle cycle = cycle("249.90", LocalDate.of(2026, 7, 3));
        when(billCycleRepository.findDue(any(), anyInt())).thenReturn(List.of(cycle));

        // Ayni tipte iki + farkli tipte bir asim: VOICE 10+5 dk = 7.50, DATA 100 MB = 5.00.
        PendingCharge voice1 = charge(cycle.getSubscriptionId(), "VOICE", "10", "0.50", "5.00");
        PendingCharge voice2 = charge(cycle.getSubscriptionId(), "VOICE", "5", "0.50", "2.50");
        PendingCharge data = charge(cycle.getSubscriptionId(), "DATA", "100", "0.05", "5.00");
        when(pendingChargeRepository.findPendingForUpdate(cycle.getSubscriptionId()))
                .thenReturn(List.of(voice1, voice2, data));

        service.runDueCycles();

        // subTotal = 249.90 + 12.50 = 262.40; KDV %20 = 52.48; genel toplam 314.88.
        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(invoiceCaptor.capture());
        Invoice invoice = invoiceCaptor.getValue();
        assertThat(invoice.getSubTotal()).isEqualByComparingTo("262.40");
        assertThat(invoice.getTax()).isEqualByComparingTo("52.48");
        assertThat(invoice.getGrandTotal()).isEqualByComparingTo("314.88");

        // Kalemler: aylik ucret + VOICE asimi (tek satirda toplanmis) + DATA asimi.
        ArgumentCaptor<InvoiceLine> lineCaptor = ArgumentCaptor.forClass(InvoiceLine.class);
        verify(invoiceLineRepository, org.mockito.Mockito.times(3)).save(lineCaptor.capture());
        List<InvoiceLine> lines = lineCaptor.getAllValues();
        assertThat(lines.get(0).getLineTotal()).isEqualByComparingTo("249.90");
        assertThat(lines.get(1).getDescription()).contains("VOICE").contains("15 dk");
        assertThat(lines.get(1).getQuantity()).isEqualByComparingTo("15");
        assertThat(lines.get(1).getLineTotal()).isEqualByComparingTo("7.50");
        assertThat(lines.get(2).getDescription()).contains("DATA");
        assertThat(lines.get(2).getLineTotal()).isEqualByComparingTo("5.00");

        // Asimlar BILLED'e cekildi ve faturaya baglandi.
        assertThat(List.of(voice1, voice2, data))
                .allSatisfy(c -> {
                    assertThat(c.getStatus()).isEqualTo("BILLED");
                    assertThat(c.getInvoiceId()).isEqualTo(invoice.getId());
                });
        verify(pendingChargeRepository).saveAll(List.of(voice1, voice2, data));

        // Auto-pay asim dahil genel toplami tahsil eder.
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outbox).enqueue(eq(SagaTopics.PAYMENT_COMMANDS), eq("ChargeInvoiceCommand"),
                eq(invoice.getId()), payloadCaptor.capture());
        assertThat(((ChargeInvoiceCommand) payloadCaptor.getValue()).amount()).isEqualByComparingTo("314.88");
    }

    @Test
    void noDueCyclesDoesNothing() {
        when(billCycleRepository.findDue(any(), anyInt())).thenReturn(List.of());
        service.runDueCycles();
        verify(invoiceRepository, org.mockito.Mockito.never()).save(any());
        verify(outbox, org.mockito.Mockito.never()).enqueue(any(), any(), any(), any());
    }

    private static BillCycle cycle(String monthlyFee, LocalDate nextRunDate) {
        BillCycle cycle = new BillCycle();
        cycle.setId(UUID.randomUUID());
        cycle.setCustomerId(UUID.randomUUID());
        cycle.setSubscriptionId(UUID.randomUUID());
        cycle.setMonthlyFee(new BigDecimal(monthlyFee));
        cycle.setCurrency("TRY");
        cycle.setNextRunDate(nextRunDate);
        return cycle;
    }

    private static PendingCharge charge(UUID subscriptionId, String type,
                                        String quantity, String unitPrice, String amount) {
        PendingCharge charge = new PendingCharge();
        charge.setId(UUID.randomUUID());
        charge.setSubscriptionId(subscriptionId);
        charge.setCustomerId(UUID.randomUUID());
        charge.setType(type);
        charge.setQuantity(new BigDecimal(quantity));
        charge.setUnitPrice(new BigDecimal(unitPrice));
        charge.setAmount(new BigDecimal(amount));
        charge.setStatus("PENDING");
        return charge;
    }
}
