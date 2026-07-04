package com.turkcell.billingservice.application.features.invoice.query.pdf;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.billingservice.dto.InvoicePdfFile;
import com.turkcell.billingservice.entity.Invoice;
import com.turkcell.billingservice.repository.BillCycleRepository;
import com.turkcell.billingservice.repository.InvoiceLineRepository;
import com.turkcell.billingservice.repository.InvoiceRepository;
import com.turkcell.billingservice.service.InvoicePdfRenderer;
import com.turkcell.commonlib.cqrs.QueryHandler;
import com.turkcell.commonlib.exception.ResourceNotFoundException;

/**
 * Fatura PDF'i (G6, FR-23): fatura + kalemler + dongu para birimi yuklenir,
 * OpenPDF ile istek aninda uretilir. Saklama yok (MinIO Faz 6).
 */
@Component
public class GetInvoicePdfQueryHandler implements QueryHandler<GetInvoicePdfQuery, InvoicePdfFile> {

    /** BillCycle silinmis/eski kayitlarda bulunamazsa PDF'te kullanilacak varsayilan. */
    private static final String DEFAULT_CURRENCY = "TRY";

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository lineRepository;
    private final BillCycleRepository billCycleRepository;
    private final InvoicePdfRenderer renderer;

    public GetInvoicePdfQueryHandler(InvoiceRepository invoiceRepository,
                                     InvoiceLineRepository lineRepository,
                                     BillCycleRepository billCycleRepository,
                                     InvoicePdfRenderer renderer) {
        this.invoiceRepository = invoiceRepository;
        this.lineRepository = lineRepository;
        this.billCycleRepository = billCycleRepository;
        this.renderer = renderer;
    }

    @Override
    @Transactional(readOnly = true)
    public InvoicePdfFile handle(GetInvoicePdfQuery query) {
        Invoice invoice = invoiceRepository.findById(query.id())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", query.id().toString()));
        String currency = invoice.getBillCycleId() == null ? DEFAULT_CURRENCY
                : billCycleRepository.findById(invoice.getBillCycleId())
                        .map(cycle -> cycle.getCurrency())
                        .orElse(DEFAULT_CURRENCY);
        byte[] content = renderer.render(invoice, lineRepository.findByInvoiceId(invoice.getId()), currency);
        return new InvoicePdfFile("fatura-" + invoice.getId() + ".pdf", content);
    }
}
