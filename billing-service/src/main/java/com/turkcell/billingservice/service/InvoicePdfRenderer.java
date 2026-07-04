package com.turkcell.billingservice.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.turkcell.billingservice.entity.Invoice;
import com.turkcell.billingservice.entity.InvoiceLine;

/**
 * Fatura PDF uretimi (G6, FR-23) — OpenPDF ile istek aninda, bellekte.
 * Metinler bilincli ASCII (font gomme derdi olmadan Helvetica yeterli).
 * Object-storage'a (MinIO) yazma Faz 6'dadir; invoices.pdf_ref o zamana kadar bos kalir.
 */
@Component
public class InvoicePdfRenderer {

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font TEXT = FontFactory.getFont(FontFactory.HELVETICA, 10);

    public byte[] render(Invoice invoice, List<InvoiceLine> lines, String currency) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4);
        PdfWriter.getInstance(doc, out);
        doc.open();

        doc.add(new Paragraph("TELCO CRM - FATURA", TITLE));
        doc.add(new Paragraph(" "));
        doc.add(meta(invoice));
        doc.add(new Paragraph(" "));
        doc.add(lineTable(lines, currency));
        doc.add(new Paragraph(" "));
        doc.add(totals(invoice, currency));

        doc.close();
        return out.toByteArray();
    }

    private PdfPTable meta(Invoice invoice) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 3});
        metaRow(table, "Fatura No", String.valueOf(invoice.getId()));
        metaRow(table, "Musteri", String.valueOf(invoice.getCustomerId()));
        metaRow(table, "Abonelik", String.valueOf(invoice.getSubscriptionId()));
        metaRow(table, "Donem", invoice.getPeriodStart() + " / " + invoice.getPeriodEnd());
        metaRow(table, "Vade", String.valueOf(invoice.getDueDate()));
        metaRow(table, "Durum", invoice.getStatus());
        return table;
    }

    private void metaRow(PdfPTable table, String label, String value) {
        table.addCell(borderless(new Phrase(label, LABEL)));
        table.addCell(borderless(new Phrase(value, TEXT)));
    }

    private PdfPCell borderless(Phrase phrase) {
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(PdfPCell.NO_BORDER);
        return cell;
    }

    private PdfPTable lineTable(List<InvoiceLine> lines, String currency) {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{5, 1, 2, 2});
        for (String header : new String[]{"Aciklama", "Miktar", "Birim Fiyat", "Tutar"}) {
            PdfPCell cell = new PdfPCell(new Phrase(header, LABEL));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
        for (InvoiceLine line : lines) {
            table.addCell(new Phrase(line.getDescription(), TEXT));
            table.addCell(right(plain(line.getQuantity())));
            table.addCell(right(money(line.getUnitPrice(), currency)));
            table.addCell(right(money(line.getLineTotal(), currency)));
        }
        return table;
    }

    private PdfPTable totals(Invoice invoice, String currency) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(40);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalRow(table, "Ara Toplam", invoice.getSubTotal(), currency);
        totalRow(table, "KDV", invoice.getTax(), currency);
        totalRow(table, "Genel Toplam", invoice.getGrandTotal(), currency);
        return table;
    }

    private void totalRow(PdfPTable table, String label, BigDecimal amount, String currency) {
        table.addCell(borderless(new Phrase(label, LABEL)));
        PdfPCell value = borderless(new Phrase(money(amount, currency), TEXT));
        value.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(value);
    }

    private PdfPCell right(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, TEXT));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return cell;
    }

    private String money(BigDecimal amount, String currency) {
        return plain(amount) + " " + currency;
    }

    private String plain(BigDecimal value) {
        return value == null ? "-" : value.toPlainString();
    }
}
