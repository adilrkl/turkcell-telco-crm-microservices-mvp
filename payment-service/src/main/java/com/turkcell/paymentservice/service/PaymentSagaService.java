package com.turkcell.paymentservice.service;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.saga.ChargeInvoiceCommand;
import com.turkcell.commonlib.saga.ChargePaymentCommand;
import com.turkcell.commonlib.saga.InvoicePaid;
import com.turkcell.commonlib.saga.InvoicePaymentFailed;
import com.turkcell.commonlib.saga.PaymentCompleted;
import com.turkcell.commonlib.saga.PaymentFailed;
import com.turkcell.commonlib.saga.PaymentRefunded;
import com.turkcell.commonlib.saga.RefundPaymentCommand;
import com.turkcell.commonlib.saga.SagaTopics;
import com.turkcell.paymentservice.entity.Payment;
import com.turkcell.paymentservice.entity.PaymentAttempt;
import com.turkcell.paymentservice.entity.ProcessedEvent;
import com.turkcell.paymentservice.repository.PaymentAttemptRepository;
import com.turkcell.paymentservice.repository.PaymentRepository;
import com.turkcell.paymentservice.repository.ProcessedEventRepository;
import com.turkcell.paymentservice.saga.OutboxWriter;

/**
 * Saga participant is mantigi (mock PSP). Tahsilat ve iade.
 * Inbox idempotency (processed_events) + reply outbox YAZIMI tek transaction'da.
 *
 * DEMO knob: tutar {@link PaymentGateway} esigini asarsa odeme REDDEDILIR. Recurring fatura
 * tahsilati basarisiz olursa dunning retry plani acilir (G8, FR-27).
 */
@Service
public class PaymentSagaService {

    private static final Logger log = LoggerFactory.getLogger(PaymentSagaService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final PaymentGateway gateway;
    private final PaymentAuditWriter audit;
    private final InvoiceChargeProcessor invoiceCharge;
    private final DunningService dunningService;
    private final OutboxWriter outbox;

    public PaymentSagaService(PaymentRepository paymentRepository,
                              PaymentAttemptRepository paymentAttemptRepository,
                              ProcessedEventRepository processedEventRepository,
                              PaymentGateway gateway,
                              PaymentAuditWriter audit,
                              InvoiceChargeProcessor invoiceCharge,
                              DunningService dunningService,
                              OutboxWriter outbox) {
        this.paymentRepository = paymentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.processedEventRepository = processedEventRepository;
        this.gateway = gateway;
        this.audit = audit;
        this.invoiceCharge = invoiceCharge;
        this.dunningService = dunningService;
        this.outbox = outbox;
    }

    @Transactional
    public void charge(ChargePaymentCommand cmd) {
        if (processedEventRepository.existsById(cmd.eventId())) {
            log.info("payment: komut zaten islendi, atlaniyor. eventId={}", cmd.eventId());
            return;
        }

        boolean approved = gateway.authorize(cmd.amount());

        Payment payment = new Payment();
        payment.setOrderId(cmd.orderId());
        payment.setCustomerId(cmd.customerId());
        payment.setAmount(cmd.amount());
        payment.setCurrency(cmd.currency());
        payment.setMethod("CARD");
        payment.setStatus(approved ? "PAID" : "FAILED");
        if (approved) {
            payment.setPaidAt(Instant.now());
            payment.setExternalRef("PSP-" + UUID.randomUUID());
        }
        payment.setCreatedAt(Instant.now());
        paymentRepository.save(payment);

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setPaymentId(payment.getId());
        attempt.setAttemptNo(1);
        attempt.setResponse(approved ? "APPROVED" : "DECLINED: tutar limiti asti");
        attempt.setAttemptedAt(Instant.now());
        paymentAttemptRepository.save(attempt);

        audit.write("Payment", payment.getId(), approved ? "CHARGE_APPROVED" : "CHARGE_DECLINED",
                "order=" + cmd.orderId() + " amount=" + cmd.amount() + " " + cmd.currency());

        if (approved) {
            outbox.enqueue(SagaTopics.SAGA_REPLIES, "PaymentCompleted", cmd.orderId(),
                    new PaymentCompleted(UUID.randomUUID(), cmd.orderId(), payment.getId()));
            log.info("payment: order={} tahsilat OK (payment={})", cmd.orderId(), payment.getId());
        } else {
            outbox.enqueue(SagaTopics.SAGA_REPLIES, "PaymentFailed", cmd.orderId(),
                    new PaymentFailed(UUID.randomUUID(), cmd.orderId(), "tutar limiti asti: " + cmd.amount()));
            log.info("payment: order={} tahsilat RED (tutar={})", cmd.orderId(), cmd.amount());
        }

        markProcessed(cmd.eventId());
    }

    /**
     * Recurring billing: bill-run faturasinin otomatik tahsilati (mock PSP).
     * Saga DISI akistir; reply saga-replies'a degil invoice-events'e doner (billing tuketir).
     * Basarisizsa InvoicePaymentFailed yayinlanir VE dunning retry plani acilir (G8, FR-27).
     */
    @Transactional
    public void chargeInvoice(ChargeInvoiceCommand cmd) {
        if (processedEventRepository.existsById(cmd.eventId())) {
            log.info("payment: komut zaten islendi, atlaniyor. eventId={}", cmd.eventId());
            return;
        }

        InvoiceChargeProcessor.Outcome outcome = invoiceCharge.charge(
                cmd.invoiceId(), cmd.customerId(), cmd.amount(), cmd.currency(), "AUTO_PAY", false);

        if (outcome.approved()) {
            outbox.enqueue(SagaTopics.INVOICE_EVENTS, "InvoicePaid", cmd.invoiceId(),
                    new InvoicePaid(UUID.randomUUID(), cmd.invoiceId(), outcome.paymentId(),
                            cmd.customerId(), cmd.amount(), cmd.currency()));
            log.info("payment: invoice={} otomatik tahsilat OK (payment={})", cmd.invoiceId(), outcome.paymentId());
        } else {
            String reason = "tutar limiti asti: " + cmd.amount();
            outbox.enqueue(SagaTopics.INVOICE_EVENTS, "InvoicePaymentFailed", cmd.invoiceId(),
                    new InvoicePaymentFailed(UUID.randomUUID(), cmd.invoiceId(), reason,
                            cmd.customerId(), cmd.amount(), cmd.currency()));
            dunningService.open(cmd.invoiceId(), cmd.customerId(), cmd.amount(), cmd.currency(),
                    reason, Instant.now());
            log.info("payment: invoice={} otomatik tahsilat RED (tutar={}) -> dunning planlandi",
                    cmd.invoiceId(), cmd.amount());
        }

        markProcessed(cmd.eventId());
    }

    @Transactional
    public void refund(RefundPaymentCommand cmd) {
        if (processedEventRepository.existsById(cmd.eventId())) {
            log.info("payment: komut zaten islendi, atlaniyor. eventId={}", cmd.eventId());
            return;
        }

        paymentRepository.findByOrderId(cmd.orderId()).ifPresent(payment -> {
            payment.setStatus("REFUNDED");
            paymentRepository.save(payment);

            PaymentAttempt attempt = new PaymentAttempt();
            attempt.setPaymentId(payment.getId());
            attempt.setAttemptNo((int) paymentAttemptRepository.countByPaymentId(payment.getId()) + 1);
            attempt.setResponse("REFUNDED: " + cmd.reason());
            attempt.setAttemptedAt(Instant.now());
            paymentAttemptRepository.save(attempt);

            audit.write("Payment", payment.getId(), "REFUNDED", "order=" + cmd.orderId() + " reason=" + cmd.reason());
            log.info("payment: order={} iade edildi (compensation)", cmd.orderId());
        });

        outbox.enqueue(SagaTopics.SAGA_REPLIES, "PaymentRefunded", cmd.orderId(),
                new PaymentRefunded(UUID.randomUUID(), cmd.orderId()));
        markProcessed(cmd.eventId());
    }

    private void markProcessed(UUID eventId) {
        ProcessedEvent pe = new ProcessedEvent();
        pe.setEventId(eventId);
        pe.setProcessedAt(Instant.now());
        processedEventRepository.save(pe);
    }
}
