package com.turkcell.notificationservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.saga.InvoiceGenerated;
import com.turkcell.commonlib.saga.InvoicePaid;
import com.turkcell.commonlib.saga.InvoicePaymentFailed;

/**
 * Fatura yasam dongusu bildirimleri (G2, docx senaryo 14.2 / §8.8), kanal EMAIL (mock).
 * Idempotency + opt-out (FR-30) {@link NotificationDispatcher}'da.
 *  - InvoiceGenerated     -> "INVOICE_GENERATED"      (faturaniz kesildi)
 *  - InvoicePaid          -> "INVOICE_PAID"           (odemeniz alindi)
 *  - InvoicePaymentFailed -> "INVOICE_PAYMENT_FAILED" (odeme basarisiz; dunning konusu)
 */
@Service
public class InvoiceEventHandler {

    private static final String TPL_GENERATED = "INVOICE_GENERATED";
    private static final String TPL_PAID = "INVOICE_PAID";
    private static final String TPL_PAYMENT_FAILED = "INVOICE_PAYMENT_FAILED";

    private final NotificationDispatcher dispatcher;

    public InvoiceEventHandler(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Transactional
    public void handleGenerated(InvoiceGenerated event) {
        dispatcher.dispatch(event.eventId(), event.customerId(), TPL_GENERATED, "EMAIL",
                "fatura kesildi (invoice=" + event.invoiceId() + ", tutar=" + event.grandTotal()
                        + " " + event.currency() + ", vade=" + event.dueDate() + ")");
    }

    @Transactional
    public void handlePaid(InvoicePaid event) {
        dispatcher.dispatch(event.eventId(), event.customerId(), TPL_PAID, "EMAIL",
                "odeme alindi (invoice=" + event.invoiceId() + ", tutar=" + event.amount()
                        + " " + event.currency() + ")");
    }

    @Transactional
    public void handlePaymentFailed(InvoicePaymentFailed event) {
        dispatcher.dispatch(event.eventId(), event.customerId(), TPL_PAYMENT_FAILED, "EMAIL",
                "odeme basarisiz (invoice=" + event.invoiceId() + ", sebep=" + event.reason() + ")");
    }
}
