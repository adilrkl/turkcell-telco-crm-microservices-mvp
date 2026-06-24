package com.turkcell.subscriptionservice.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.saga.ActivateSubscriptionCommand;
import com.turkcell.commonlib.saga.MsisdnReleased;
import com.turkcell.commonlib.saga.MsisdnReservationFailed;
import com.turkcell.commonlib.saga.MsisdnReserved;
import com.turkcell.commonlib.saga.ReleaseMsisdnCommand;
import com.turkcell.commonlib.saga.ReserveMsisdnCommand;
import com.turkcell.commonlib.saga.SagaTopics;
import com.turkcell.commonlib.saga.SubscriptionActivated;
import com.turkcell.commonlib.saga.SubscriptionActivationFailed;
import com.turkcell.subscriptionservice.entity.AuditLog;
import com.turkcell.subscriptionservice.entity.MsisdnPool;
import com.turkcell.subscriptionservice.entity.ProcessedEvent;
import com.turkcell.subscriptionservice.entity.SimCard;
import com.turkcell.subscriptionservice.entity.Subscription;
import com.turkcell.subscriptionservice.repository.AuditLogRepository;
import com.turkcell.subscriptionservice.repository.MsisdnPoolRepository;
import com.turkcell.subscriptionservice.repository.ProcessedEventRepository;
import com.turkcell.subscriptionservice.repository.SimCardRepository;
import com.turkcell.subscriptionservice.repository.SubscriptionRepository;
import com.turkcell.subscriptionservice.saga.OutboxWriter;

/**
 * Saga participant is mantigi. MSISDN reserve -> activate -> release (compensation).
 * Inbox idempotency (processed_events) + reply outbox YAZIMI tek transaction'da.
 *
 * DEMO knob: tarife kodu "_FAIL" ile bitiyorsa aktivasyon REDDEDILIR
 * (SubscriptionActivationFailed -> order compensation: RefundPayment + ReleaseMsisdn).
 */
@Service
public class SubscriptionSagaService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionSagaService.class);
    private static final Duration RESERVATION_TTL = Duration.ofMinutes(15);

    private final SubscriptionRepository subscriptionRepository;
    private final MsisdnPoolRepository msisdnPoolRepository;
    private final SimCardRepository simCardRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final AuditLogRepository auditLogRepository;
    private final OutboxWriter outbox;

    public SubscriptionSagaService(SubscriptionRepository subscriptionRepository,
                                   MsisdnPoolRepository msisdnPoolRepository,
                                   SimCardRepository simCardRepository,
                                   ProcessedEventRepository processedEventRepository,
                                   AuditLogRepository auditLogRepository,
                                   OutboxWriter outbox) {
        this.subscriptionRepository = subscriptionRepository;
        this.msisdnPoolRepository = msisdnPoolRepository;
        this.simCardRepository = simCardRepository;
        this.processedEventRepository = processedEventRepository;
        this.auditLogRepository = auditLogRepository;
        this.outbox = outbox;
    }

    @Transactional
    public void reserve(ReserveMsisdnCommand cmd) {
        if (seen(cmd.eventId())) return;

        Optional<MsisdnPool> free = msisdnPoolRepository.findFirstByStatusOrderByMsisdn("FREE");
        if (free.isEmpty()) {
            outbox.enqueue(SagaTopics.SAGA_REPLIES, "MsisdnReservationFailed", cmd.orderId(),
                    new MsisdnReservationFailed(UUID.randomUUID(), cmd.orderId(), "MSISDN havuzunda bos numara yok"));
            log.warn("subscription: order={} rezervasyon FAILED (havuz bos)", cmd.orderId());
            markProcessed(cmd.eventId());
            return;
        }

        MsisdnPool pool = free.get();
        pool.setStatus("RESERVED");
        pool.setReservedUntil(Instant.now().plus(RESERVATION_TTL));
        msisdnPoolRepository.save(pool);

        Subscription sub = new Subscription();
        sub.setOrderId(cmd.orderId());
        sub.setCustomerId(cmd.customerId());
        sub.setMsisdn(pool.getMsisdn());
        sub.setTariffCode(cmd.tariffCode());
        sub.setStatus("PENDING");
        subscriptionRepository.save(sub);

        audit("Subscription", sub.getId(), "MSISDN_RESERVED", "order=" + cmd.orderId() + " msisdn=" + pool.getMsisdn());
        outbox.enqueue(SagaTopics.SAGA_REPLIES, "MsisdnReserved", cmd.orderId(),
                new MsisdnReserved(UUID.randomUUID(), cmd.orderId(), pool.getMsisdn()));
        log.info("subscription: order={} MSISDN rezerve {} (PENDING)", cmd.orderId(), pool.getMsisdn());
        markProcessed(cmd.eventId());
    }

    @Transactional
    public void activate(ActivateSubscriptionCommand cmd) {
        if (seen(cmd.eventId())) return;

        Subscription sub = subscriptionRepository.findByOrderId(cmd.orderId()).orElse(null);
        if (sub == null) {
            outbox.enqueue(SagaTopics.SAGA_REPLIES, "SubscriptionActivationFailed", cmd.orderId(),
                    new SubscriptionActivationFailed(UUID.randomUUID(), cmd.orderId(), "abonelik bulunamadi"));
            markProcessed(cmd.eventId());
            return;
        }

        // DEMO knob: tarife _FAIL ile bitiyorsa provisioning hatasi simule et.
        if (sub.getTariffCode() != null && sub.getTariffCode().endsWith("_FAIL")) {
            audit("Subscription", sub.getId(), "ACTIVATION_FAILED", "order=" + cmd.orderId() + " (simulated)");
            outbox.enqueue(SagaTopics.SAGA_REPLIES, "SubscriptionActivationFailed", cmd.orderId(),
                    new SubscriptionActivationFailed(UUID.randomUUID(), cmd.orderId(), "provisioning hatasi (simulated)"));
            log.info("subscription: order={} aktivasyon RED (simulated)", cmd.orderId());
            markProcessed(cmd.eventId());
            return;
        }

        // MSISDN RESERVED -> ALLOCATED, SIM ata, abonelik ACTIVE.
        msisdnPoolRepository.findById(sub.getMsisdn()).ifPresent(pool -> {
            pool.setStatus("ALLOCATED");
            pool.setReservedUntil(null);
            msisdnPoolRepository.save(pool);
        });

        SimCard sim = newSimCard(sub.getMsisdn());
        simCardRepository.save(sim);

        sub.setStatus("ACTIVE");
        sub.setActivatedAt(Instant.now());
        subscriptionRepository.save(sub);

        audit("Subscription", sub.getId(), "ACTIVATED",
                "order=" + cmd.orderId() + " msisdn=" + sub.getMsisdn() + " iccid=" + sim.getIccid());
        outbox.enqueue(SagaTopics.SAGA_REPLIES, "SubscriptionActivated", cmd.orderId(),
                new SubscriptionActivated(UUID.randomUUID(), cmd.orderId(), sub.getId(), sub.getMsisdn()));
        log.info("subscription: order={} ACTIVE (sub={}, msisdn={})", cmd.orderId(), sub.getId(), sub.getMsisdn());
        markProcessed(cmd.eventId());
    }

    @Transactional
    public void release(ReleaseMsisdnCommand cmd) {
        if (seen(cmd.eventId())) return;

        subscriptionRepository.findByOrderId(cmd.orderId()).ifPresent(sub -> {
            sub.setStatus("CANCELLED");
            sub.setTerminatedAt(Instant.now());
            subscriptionRepository.save(sub);

            msisdnPoolRepository.findById(sub.getMsisdn()).ifPresent(pool -> {
                pool.setStatus("FREE");
                pool.setReservedUntil(null);
                msisdnPoolRepository.save(pool);
            });

            audit("Subscription", sub.getId(), "RELEASED", "order=" + cmd.orderId() + " reason=" + cmd.reason());
            log.info("subscription: order={} rezervasyon/abonelik birakildi (compensation)", cmd.orderId());
        });

        outbox.enqueue(SagaTopics.SAGA_REPLIES, "MsisdnReleased", cmd.orderId(),
                new MsisdnReleased(UUID.randomUUID(), cmd.orderId()));
        markProcessed(cmd.eventId());
    }

    /** Demo SIM: rakamsal ICCID(22)/IMSI(15) uretir (mock). */
    private SimCard newSimCard(String msisdn) {
        long n = Math.abs(UUID.randomUUID().getMostSignificantBits());
        SimCard sim = new SimCard();
        sim.setIccid(String.format("8990%018d", n % 1_000_000_000_000_000_000L));
        sim.setImsi(String.format("28601%010d", n % 10_000_000_000L));
        sim.setMsisdn(msisdn);
        sim.setStatus("ACTIVE");
        return sim;
    }

    private void audit(String entity, UUID entityId, String action, String detail) {
        AuditLog a = new AuditLog();
        a.setServiceName("subscription-service");
        a.setEntityName(entity);
        a.setEntityId(entityId);
        a.setAction(action);
        a.setDetail(detail);
        a.setCreatedAt(Instant.now());
        auditLogRepository.save(a);
    }

    private boolean seen(UUID eventId) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("subscription: komut zaten islendi, atlaniyor. eventId={}", eventId);
            return true;
        }
        return false;
    }

    private void markProcessed(UUID eventId) {
        ProcessedEvent pe = new ProcessedEvent();
        pe.setEventId(eventId);
        pe.setProcessedAt(Instant.now());
        processedEventRepository.save(pe);
    }
}
