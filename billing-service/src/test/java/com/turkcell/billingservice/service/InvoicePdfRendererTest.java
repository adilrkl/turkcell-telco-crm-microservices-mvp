package com.turkcell.billingservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.turkcell.billingservice.entity.Invoice;
import com.turkcell.billingservice.entity.InvoiceLine;

/** Fatura PDF uretimi (G6, FR-23): cikti gecerli PDF mi ve fatura verilerini iceriyor mu. */
class InvoicePdfRendererTest {

    private final InvoicePdfRenderer renderer = new InvoicePdfRenderer();

    @Test
    void rendersParsablePdfContainingInvoiceData() throws Exception {
        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setCustomerId(UUID.randomUUID());
        invoice.setSubscriptionId(UUID.randomUUID());
        invoice.setPeriodStart(LocalDate.of(2026, 6, 3));
        invoice.setPeriodEnd(LocalDate.of(2026, 7, 3));
        invoice.setDueDate(LocalDate.of(2026, 7, 18));
        invoice.setStatus("ISSUED");
        invoice.setSubTotal(new BigDecimal("249.90"));
        invoice.setTax(new BigDecimal("49.98"));
        invoice.setGrandTotal(new BigDecimal("299.88"));

        InvoiceLine line = new InvoiceLine();
        line.setDescription("Aylik ucret - TARIFE_M");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal("249.90"));
        line.setLineTotal(new BigDecimal("249.90"));

        byte[] pdf = renderer.render(invoice, List.of(line), "TRY");

        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        String text = new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1);
        assertThat(text)
                .contains("FATURA")
                .contains(invoice.getId().toString())
                .contains("Aylik ucret - TARIFE_M")
                .contains("249.90 TRY")
                .contains("299.88 TRY")
                .contains("ISSUED");
    }

    @Test
    void rendersValidPdfEvenWithoutLinesAndOptionalFields() {
        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setStatus("DRAFT");

        byte[] pdf = renderer.render(invoice, List.of(), "TRY");

        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(pdf.length).isGreaterThan(500);
    }
}
