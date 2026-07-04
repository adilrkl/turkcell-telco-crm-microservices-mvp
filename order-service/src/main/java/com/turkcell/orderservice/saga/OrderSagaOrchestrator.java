package com.turkcell.orderservice.saga;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.saga.ActivateSubscriptionCommand;
import com.turkcell.commonlib.saga.ChargePaymentCommand;
import com.turkcell.commonlib.saga.MsisdnReleased;
import com.turkcell.commonlib.saga.MsisdnReservationFailed;
import com.turkcell.commonlib.saga.MsisdnReserved;
import com.turkcell.commonlib.saga.OrderCancelled;
import com.turkcell.commonlib.saga.OrderConfirmed;
import com.turkcell.commonlib.saga.PaymentCompleted;
import com.turkcell.commonlib.saga.PaymentFailed;
import com.turkcell.commonlib.saga.PaymentRefunded;
import com.turkcell.commonlib.saga.RefundPaymentCommand;
import com.turkcell.commonlib.saga.ReleaseMsisdnCommand;
import com.turkcell.commonlib.saga.ReserveMsisdnCommand;
import com.turkcell.commonlib.saga.SagaTopics;
import com.turkcell.commonlib.saga.SubscriptionActivated;
import com.turkcell.commonlib.saga.SubscriptionActivationFailed;
import com.turkcell.orderservice.entity.Order;
import com.turkcell.orderservice.entity.ProcessedEvent;
import com.turkcell.orderservice.entity.SagaState;
import com.turkcell.orderservice.repository.OrderRepository;
import com.turkcell.orderservice.repository.ProcessedEventRepository;
import com.turkcell.orderservice.repository.SagaStateRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Saga orchestrator: order = saga sahibi. Reply event'lerini dinler, saga_states'i
 * ilerletir, sonraki komutu/domain event'i transactional outbox'a yazar.
 *
 * <pre>
 * ReserveMsisdn -> MsisdnReserved -> ChargePayment -> PaymentCompleted -> ActivateSubscription
 *               -> SubscriptionActivated -> (order FULFILLED, OrderConfirmed)
 * Hata yolunda compensation: ReleaseMsisdn / RefundPayment + order CANCELLED + OrderCancelled.
 * </pre>
 *
 * Tum gecisler idempotent: ayni eventId tekrar gelirse processed_events ile atlanir.
 */
@Service
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    private final SagaStateRepository sagaRepository;
    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxWriter outbox;
    private final ObjectMapper objectMapper;

    public OrderSagaOrchestrator(SagaStateRepository sagaRepository,
                                 OrderRepository orderRepository,
                                 ProcessedEventRepository processedEventRepository,
                                 OutboxWriter outbox,
                                 ObjectMapper objectMapper) {
        this.sagaRepository = sagaRepository;
        this.orderRepository = orderRepository;
        this.processedEventRepository = processedEventRepository;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /** Siparis yaziminin transaction'i icinde cagrilir: saga'yi baslatir ve ilk komutu kuyruga koyar. */
    public void start(Order order, UUID customerId, String tariffCode, BigDecimal amount, String currency,
                      Integer minutesIncluded, Integer smsIncluded, Integer dataMbIncluded) {
        SagaContext ctx = new SagaContext(customerId, tariffCode, amount, currency, null, null, null,
                minutesIncluded, smsIncluded, dataMbIncluded);
        SagaState saga = new SagaState();
        saga.setOrderId(order.getId());
        saga.setCurrentStep(SagaSteps.STARTED);
        saga.setPayload(objectMapper.writeValueAsString(ctx));
        saga.setLastUpdated(Instant.now());
        sagaRepository.save(saga);

        outbox.enqueue(SagaTopics.SUBSCRIPTION_COMMANDS, "ReserveMsisdnCommand", order.getId(),
                new ReserveMsisdnCommand(UUID.randomUUID(), order.getId(), customerId, tariffCode));
        log.info("Saga basladi order={} -> ReserveMsisdn", order.getId());
    }

    // --- Reply isleyicileri (saga-replies topic'inden gelir) ---

    @Transactional
    public void onMsisdnReserved(MsisdnReserved ev) {
        if (seen(ev.eventId())) return;
        SagaState saga = expect(ev.orderId(), SagaSteps.STARTED);
        if (saga != null) {
            SagaContext ctx = ctx(saga).withMsisdn(ev.msisdn());
            advance(saga, SagaSteps.MSISDN_RESERVED, ctx);
            outbox.enqueue(SagaTopics.PAYMENT_COMMANDS, "ChargePaymentCommand", ev.orderId(),
                    new ChargePaymentCommand(UUID.randomUUID(), ev.orderId(), ctx.customerId(), ctx.amount(), ctx.currency()));
            log.info("order={} MSISDN rezerve ({}) -> ChargePayment", ev.orderId(), ev.msisdn());
        }
        markProcessed(ev.eventId());
    }

    @Transactional
    public void onMsisdnReservationFailed(MsisdnReservationFailed ev) {
        if (seen(ev.eventId())) return;
        SagaState saga = expect(ev.orderId(), SagaSteps.STARTED);
        if (saga != null) {
            // Henuz hicbir sey rezerve/charge edilmedi; compensation yok, sadece iptal.
            cancel(saga, ev.orderId(), "MSISDN rezerve edilemedi: " + ev.reason());
        }
        markProcessed(ev.eventId());
    }

    @Transactional
    public void onPaymentCompleted(PaymentCompleted ev) {
        if (seen(ev.eventId())) return;
        SagaState saga = expect(ev.orderId(), SagaSteps.MSISDN_RESERVED);
        if (saga != null) {
            SagaContext ctx = ctx(saga).withPaymentId(ev.paymentId());
            advance(saga, SagaSteps.PAYMENT_COMPLETED, ctx);
            setOrderStatus(ev.orderId(), OrderStatus.PAID);
            outbox.enqueue(SagaTopics.SUBSCRIPTION_COMMANDS, "ActivateSubscriptionCommand", ev.orderId(),
                    new ActivateSubscriptionCommand(UUID.randomUUID(), ev.orderId()));
            log.info("order={} odeme tamam (payment={}) -> ActivateSubscription", ev.orderId(), ev.paymentId());
        }
        markProcessed(ev.eventId());
    }

    @Transactional
    public void onPaymentFailed(PaymentFailed ev) {
        if (seen(ev.eventId())) return;
        SagaState saga = expect(ev.orderId(), SagaSteps.MSISDN_RESERVED);
        if (saga != null) {
            // Compensation: rezerve numarayi birak. Para hareket etmedi (refund gerekmez).
            outbox.enqueue(SagaTopics.SUBSCRIPTION_COMMANDS, "ReleaseMsisdnCommand", ev.orderId(),
                    new ReleaseMsisdnCommand(UUID.randomUUID(), ev.orderId(), "payment failed"));
            cancel(saga, ev.orderId(), "Odeme basarisiz: " + ev.reason());
        }
        markProcessed(ev.eventId());
    }

    @Transactional
    public void onSubscriptionActivated(SubscriptionActivated ev) {
        if (seen(ev.eventId())) return;
        SagaState saga = expect(ev.orderId(), SagaSteps.PAYMENT_COMPLETED);
        if (saga != null) {
            SagaContext ctx = ctx(saga).withSubscriptionId(ev.subscriptionId()).withMsisdn(ev.msisdn());
            advance(saga, SagaSteps.COMPLETED, ctx);
            setOrderStatus(ev.orderId(), OrderStatus.FULFILLED);
            outbox.enqueue(SagaTopics.ORDER_EVENTS, "OrderConfirmed", ev.orderId(),
                    new OrderConfirmed(UUID.randomUUID(), ev.orderId(), ctx.customerId(), ev.subscriptionId(),
                            ctx.tariffCode(), ctx.msisdn(), ctx.amount(), ctx.currency(),
                            ctx.minutesIncluded(), ctx.smsIncluded(), ctx.dataMbIncluded()));
            log.info("order={} abonelik aktif (sub={}) -> FULFILLED", ev.orderId(), ev.subscriptionId());
        }
        markProcessed(ev.eventId());
    }

    @Transactional
    public void onSubscriptionActivationFailed(SubscriptionActivationFailed ev) {
        if (seen(ev.eventId())) return;
        SagaState saga = expect(ev.orderId(), SagaSteps.PAYMENT_COMPLETED);
        if (saga != null) {
            // Compensation: odeme tahsil edilmisti -> iade + rezervasyonu birak.
            outbox.enqueue(SagaTopics.PAYMENT_COMMANDS, "RefundPaymentCommand", ev.orderId(),
                    new RefundPaymentCommand(UUID.randomUUID(), ev.orderId(), "activation failed"));
            outbox.enqueue(SagaTopics.SUBSCRIPTION_COMMANDS, "ReleaseMsisdnCommand", ev.orderId(),
                    new ReleaseMsisdnCommand(UUID.randomUUID(), ev.orderId(), "activation failed"));
            cancel(saga, ev.orderId(), "Aktivasyon basarisiz: " + ev.reason());
        }
        markProcessed(ev.eventId());
    }

    @Transactional
    public void onMsisdnReleased(MsisdnReleased ev) {
        if (seen(ev.eventId())) return;
        log.info("order={} compensation ack: MSISDN birakildi", ev.orderId());
        markProcessed(ev.eventId());
    }

    @Transactional
    public void onPaymentRefunded(PaymentRefunded ev) {
        if (seen(ev.eventId())) return;
        log.info("order={} compensation ack: odeme iade edildi", ev.orderId());
        markProcessed(ev.eventId());
    }

    /**
     * Timeout suupuru: cevap gelmeyen (terminal olmayan) saga'lari iptal eder ve ulasilan
     * adima gore compensation tetikler. SagaTimeoutScheduler periyodik cagirir.
     */
    @Transactional
    public void sweepTimeouts(Instant cutoff) {
        for (SagaState saga : sagaRepository.findByCurrentStepNotInAndLastUpdatedBefore(SagaSteps.TERMINAL, cutoff)) {
            String step = saga.getCurrentStep();
            UUID orderId = saga.getOrderId();
            log.warn("Saga timeout order={} step={} -> iptal + compensation", orderId, step);
            if (SagaSteps.PAYMENT_COMPLETED.equals(step)) {
                outbox.enqueue(SagaTopics.PAYMENT_COMMANDS, "RefundPaymentCommand", orderId,
                        new RefundPaymentCommand(UUID.randomUUID(), orderId, "saga timeout"));
                outbox.enqueue(SagaTopics.SUBSCRIPTION_COMMANDS, "ReleaseMsisdnCommand", orderId,
                        new ReleaseMsisdnCommand(UUID.randomUUID(), orderId, "saga timeout"));
            } else if (SagaSteps.MSISDN_RESERVED.equals(step)) {
                outbox.enqueue(SagaTopics.SUBSCRIPTION_COMMANDS, "ReleaseMsisdnCommand", orderId,
                        new ReleaseMsisdnCommand(UUID.randomUUID(), orderId, "saga timeout"));
            }
            cancel(saga, orderId, "Saga zaman asimina ugradi (step=" + step + ")");
        }
    }

    // --- yardimcilar ---

    private void cancel(SagaState saga, UUID orderId, String reason) {
        advance(saga, SagaSteps.CANCELLED, ctx(saga));
        setOrderStatus(orderId, OrderStatus.CANCELLED);
        SagaContext ctx = ctx(saga);
        outbox.enqueue(SagaTopics.ORDER_EVENTS, "OrderCancelled", orderId,
                new OrderCancelled(UUID.randomUUID(), orderId, ctx.customerId(), reason));
        log.info("order={} CANCELLED: {}", orderId, reason);
    }

    private void advance(SagaState saga, String step, SagaContext ctx) {
        saga.setCurrentStep(step);
        saga.setPayload(objectMapper.writeValueAsString(ctx));
        saga.setLastUpdated(Instant.now());
        sagaRepository.save(saga);
    }

    private void setOrderStatus(UUID orderId, String status) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus(status);
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);
        });
    }

    /** Saga'yi yukle ve beklenen adimda mi dogrula. Degilse (out-of-order/tekrar) null doner. */
    private SagaState expect(UUID orderId, String expectedStep) {
        SagaState saga = sagaRepository.findByOrderId(orderId).orElse(null);
        if (saga == null) {
            log.warn("Saga bulunamadi order={} (reply atlandi)", orderId);
            return null;
        }
        if (!expectedStep.equals(saga.getCurrentStep())) {
            log.warn("Saga adim uyumsuz order={} beklenen={} mevcut={} (reply atlandi)",
                    orderId, expectedStep, saga.getCurrentStep());
            return null;
        }
        return saga;
    }

    private SagaContext ctx(SagaState saga) {
        return objectMapper.readValue(saga.getPayload(), SagaContext.class);
    }

    private boolean seen(UUID eventId) {
        return processedEventRepository.existsById(eventId);
    }

    private void markProcessed(UUID eventId) {
        ProcessedEvent pe = new ProcessedEvent();
        pe.setEventId(eventId);
        pe.setProcessedAt(Instant.now());
        processedEventRepository.save(pe);
    }
}
