package com.turkcell.billingservice.application.features.invoice.query.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.turkcell.billingservice.dto.InvoicePdfFile;
import com.turkcell.billingservice.entity.BillCycle;
import com.turkcell.billingservice.entity.Invoice;
import com.turkcell.billingservice.repository.BillCycleRepository;
import com.turkcell.billingservice.repository.InvoiceLineRepository;
import com.turkcell.billingservice.repository.InvoiceRepository;
import com.turkcell.billingservice.service.InvoicePdfRenderer;
import com.turkcell.commonlib.exception.ResourceNotFoundException;

/** PDF sorgu handler'i: 404, dosya adi ve para birimi cozumu (dongu -> varsayilan TRY). */
class GetInvoicePdfQueryHandlerTest {

    private InvoiceRepository invoiceRepository;
    private InvoiceLineRepository lineRepository;
    private BillCycleRepository billCycleRepository;
    private InvoicePdfRenderer renderer;
    private GetInvoicePdfQueryHandler handler;

    private final UUID invoiceId = UUID.randomUUID();
    private final UUID cycleId = UUID.randomUUID();
    private final byte[] pdfBytes = {'%', 'P', 'D', 'F'};

    @BeforeEach
    void setUp() {
        invoiceRepository = mock(InvoiceRepository.class);
        lineRepository = mock(InvoiceLineRepository.class);
        billCycleRepository = mock(BillCycleRepository.class);
        renderer = mock(InvoicePdfRenderer.class);
        handler = new GetInvoicePdfQueryHandler(invoiceRepository, lineRepository, billCycleRepository, renderer);
    }

    @Test
    void returnsNamedPdfUsingCycleCurrency() {
        Invoice invoice = invoice(cycleId);
        BillCycle cycle = new BillCycle();
        cycle.setCurrency("EUR");
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(billCycleRepository.findById(cycleId)).thenReturn(Optional.of(cycle));
        when(lineRepository.findByInvoiceId(invoiceId)).thenReturn(List.of());
        when(renderer.render(eq(invoice), eq(List.of()), eq("EUR"))).thenReturn(pdfBytes);

        InvoicePdfFile file = handler.handle(new GetInvoicePdfQuery(invoiceId));

        assertThat(file.fileName()).isEqualTo("fatura-" + invoiceId + ".pdf");
        assertThat(file.content()).isEqualTo(pdfBytes);
    }

    @Test
    void fallsBackToTryWhenCycleMissing() {
        Invoice invoice = invoice(null);
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(lineRepository.findByInvoiceId(invoiceId)).thenReturn(List.of());
        when(renderer.render(eq(invoice), eq(List.of()), eq("TRY"))).thenReturn(pdfBytes);

        InvoicePdfFile file = handler.handle(new GetInvoicePdfQuery(invoiceId));

        assertThat(file.content()).isEqualTo(pdfBytes);
    }

    @Test
    void throwsNotFoundWhenInvoiceMissing() {
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetInvoicePdfQuery(invoiceId)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Invoice invoice(UUID billCycleId) {
        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);
        invoice.setBillCycleId(billCycleId);
        return invoice;
    }
}
