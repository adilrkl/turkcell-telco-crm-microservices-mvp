package com.turkcell.paymentservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.saga.ChargePaymentCommand;
import com.turkcell.commonlib.saga.PaymentCompleted;
import com.turkcell.commonlib.saga.PaymentFailed;
import com.turkcell.commonlib.saga.PaymentRefunded;
import com.turkcell.commonlib.saga.RefundPaymentCommand;
import com.turkcell.commonlib.saga.SagaTopics;
import com.turkcell.paymentservice.entity.AuditLog;
import com.turkcell.paymentservice.entity.Payment;
import com.turkcell.paymentservice.entity.PaymentAttempt;
import com.turkcell.paymentservice.entity.ProcessedEvent;
import com.turkcell.paymentservice.repository.AuditLogRepository;
import com.turkcell.paymentservice.repository.PaymentAttemptRepository;
import com.turkcell.paymentservice.repository.PaymentRepository;
import com.turkcell.paymentservice.repository.ProcessedEventRepository;
import com.turkcell.paymentservice.saga.OutboxWriter;

/**
 * Saga participant is mantigi (mock PSP). Tahsilat ve iade.
 * Inbox idempotency (processed_events) + reply outbox YAZIMI tek transaction'da.
 *
 * DEMO knob: tutar {@link #FAIL_THRESHOLD} TRY'yi asarsa odeme REDDEDILIR
 * (PaymentFailed -> order compensation: ReleaseMsisdn). Yuksek ucretli tarife siparis ederek tetiklenir.
 */
@Service
public class PaymentSagaService {

    private static final Logger log = LoggerFactory.getLogger(PaymentSagaService.class);
    private static final BigDecimal FAIL_THRESHOLD = new BigDecimal("1000");

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final AuditLogRepository auditLogRepository;
    private final OutboxWriter outbox;

    public PaymentSagaService(PaymentRepository paymentRepository,
                              PaymentAttemptRepository paymentAttemptRepository,
                              ProcessedEventRepository processedEventRepository,
                              AuditLogRepository auditLogRepository,
                              OutboxWriter outbox) {
        this.paymentRepository = paymentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.processedEventRepository = processedEventRepository;
        this.auditLogRepository = auditLogRepository;
        this.outbox = outbox;
    }

    @Transactional
    public void charge(ChargePaymentCommand cmd) {
        if (processedEventRepository.existsById(cmd.eventId())) {
            log.info("payment: komut zaten islendi, atlaniyor. eventId={}", cmd.eventId());
            return;
        }

        boolean approved = cmd.amount().compareTo(FAIL_THRESHOLD) <= 0;

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

        audit("Payment", payment.getId(), approved ? "CHARGE_APPROVED" : "CHARGE_DECLINED",
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

            audit("Payment", payment.getId(), "REFUNDED", "order=" + cmd.orderId() + " reason=" + cmd.reason());
            log.info("payment: order={} iade edildi (compensation)", cmd.orderId());
        });

        outbox.enqueue(SagaTopics.SAGA_REPLIES, "PaymentRefunded", cmd.orderId(),
                new PaymentRefunded(UUID.randomUUID(), cmd.orderId()));
        markProcessed(cmd.eventId());
    }

    private void audit(String entity, UUID entityId, String action, String detail) {
        AuditLog a = new AuditLog();
        a.setServiceName("payment-service");
        a.setEntityName(entity);
        a.setEntityId(entityId);
        a.setAction(action);
        a.setDetail(detail);
        a.setCreatedAt(Instant.now());
        auditLogRepository.save(a);
    }

    private void markProcessed(UUID eventId) {
        ProcessedEvent pe = new ProcessedEvent();
        pe.setEventId(eventId);
        pe.setProcessedAt(Instant.now());
        processedEventRepository.save(pe);
    }
}
