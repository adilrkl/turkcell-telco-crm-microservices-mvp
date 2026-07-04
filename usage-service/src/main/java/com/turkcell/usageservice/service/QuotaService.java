package com.turkcell.usageservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.saga.OrderConfirmed;
import com.turkcell.commonlib.saga.OverageRecorded;
import com.turkcell.commonlib.saga.QuotaThresholdReached;
import com.turkcell.commonlib.saga.SagaTopics;
import com.turkcell.usageservice.entity.Quota;
import com.turkcell.usageservice.entity.SubscriptionEntitlement;
import com.turkcell.usageservice.repository.QuotaRepository;
import com.turkcell.usageservice.repository.SubscriptionEntitlementRepository;
import com.turkcell.usageservice.saga.OutboxWriter;

/**
 * Kota zinciri cekirdegi (G1 / FR-17..20):
 * <ul>
 *   <li>{@link #provision(OrderConfirmed)}: tarife hak fotografini yazar + ilk donem kotasini acar.</li>
 *   <li>{@link #applyUsage}: kullanimi kotadan duser; %80/%100 esik gecisinde QuotaThresholdReached,
 *       kota asan miktar icin OverageRecorded outbox'a yazilir (quota-events).</li>
 * </ul>
 * Iki metod da cagiranin transaction'ina katilir (inbox kontrolu + usage yazimi ile atomik).
 * Donem = recordedAt'in UTC takvim ayi; yeni donemin kotasi hak fotografinden lazy acilir.
 */
@Service
public class QuotaService {

    private static final Logger log = LoggerFactory.getLogger(QuotaService.class);
    private static final BigDecimal HUNDRED = new BigDecimal(100);
    private static final BigDecimal WARN_PCT = new BigDecimal(80);

    private final QuotaRepository quotaRepository;
    private final SubscriptionEntitlementRepository entitlementRepository;
    private final OutboxWriter outbox;

    public QuotaService(QuotaRepository quotaRepository,
                        SubscriptionEntitlementRepository entitlementRepository,
                        OutboxWriter outbox) {
        this.quotaRepository = quotaRepository;
        this.entitlementRepository = entitlementRepository;
        this.outbox = outbox;
    }

    /** OrderConfirmed -> hak fotografi (upsert) + icinde bulunulan donemin kotasi. */
    @Transactional
    public void provision(OrderConfirmed event) {
        SubscriptionEntitlement ent = entitlementRepository.findById(event.subscriptionId())
                .orElseGet(SubscriptionEntitlement::new);
        if (ent.getSubscriptionId() == null) {
            ent.setSubscriptionId(event.subscriptionId());
            ent.setCreatedAt(Instant.now());
        }
        ent.setCustomerId(event.customerId());
        ent.setMsisdn(event.msisdn());
        ent.setTariffCode(event.tariffCode());
        ent.setMinutesIncluded(event.minutesIncluded());
        ent.setSmsIncluded(event.smsIncluded());
        ent.setMbIncluded(event.dataMbIncluded());
        entitlementRepository.save(ent);

        LocalDate periodStart = periodStartOf(Instant.now());
        if (quotaRepository.findBySubscriptionIdAndPeriodStart(event.subscriptionId(), periodStart).isEmpty()) {
            quotaRepository.save(openQuota(ent, periodStart));
        }
        log.info("quota: hak fotografi yazildi + kota acildi. sub={} tarife={} dk={} sms={} mb={}",
                event.subscriptionId(), event.tariffCode(),
                event.minutesIncluded(), event.smsIncluded(), event.dataMbIncluded());
    }

    /**
     * Kullanimi ilgili donemin kotasindan duser. Hak fotografi olmayan abonelik
     * (orn. eski/deneme verisi) kota takibine girmez; kayit yine de yazilmistir.
     */
    @Transactional
    public void applyUsage(UUID subscriptionId, String type, BigDecimal quantity, Instant recordedAt) {
        if (quantity == null || quantity.signum() <= 0) {
            return;
        }
        SubscriptionEntitlement ent = entitlementRepository.findById(subscriptionId).orElse(null);
        if (ent == null) {
            log.debug("quota: hak fotografi yok, kota takibi atlandi. sub={}", subscriptionId);
            return;
        }

        LocalDate periodStart = periodStartOf(recordedAt != null ? recordedAt : Instant.now());
        Quota quota = quotaRepository.findForUpdate(subscriptionId, periodStart)
                .orElseGet(() -> quotaRepository.save(openQuota(ent, periodStart)));

        BigDecimal total = totalOf(quota, type);
        if (total == null || total.signum() <= 0) {
            log.debug("quota: {} icin hak tanimsiz, dusum yok. sub={}", type, subscriptionId);
            return;
        }

        BigDecimal before = remainingOf(quota, type);
        BigDecimal overage = quantity.subtract(before).max(BigDecimal.ZERO);
        BigDecimal remaining = before.subtract(quantity).max(BigDecimal.ZERO);
        setRemaining(quota, type, remaining);

        publishThresholdIfCrossed(quota, ent, type, total, remaining);
        if (overage.signum() > 0) {
            outbox.enqueue(SagaTopics.QUOTA_EVENTS, "OverageRecorded", subscriptionId,
                    new OverageRecorded(UUID.randomUUID(), subscriptionId, ent.getCustomerId(),
                            type, overage, quota.getPeriodStart(), quota.getPeriodEnd()));
            log.info("quota: asim kaydedildi. sub={} type={} asim={}", subscriptionId, type, overage);
        }
        quotaRepository.save(quota);
    }

    /**
     * Esik gecisi kontrolu: donem icinde esik basina EN FAZLA bir event (warnedPct 0->80->100).
     * Tek kullanim her iki esigi birden asarsa yalnizca en yuksek olan (%100) yayinlanir.
     */
    private void publishThresholdIfCrossed(Quota quota, SubscriptionEntitlement ent,
                                           String type, BigDecimal total, BigDecimal remaining) {
        BigDecimal used = total.subtract(remaining);
        boolean crossed100 = remaining.signum() == 0;
        boolean crossed80 = used.multiply(HUNDRED).compareTo(total.multiply(WARN_PCT)) >= 0;
        short warned = warnedPctOf(quota, type);

        int threshold;
        if (crossed100 && warned < 100) {
            threshold = 100;
        } else if (crossed80 && warned < 80) {
            threshold = 80;
        } else {
            return;
        }
        setWarnedPct(quota, type, (short) threshold);
        outbox.enqueue(SagaTopics.QUOTA_EVENTS, "QuotaThresholdReached", quota.getSubscriptionId(),
                new QuotaThresholdReached(UUID.randomUUID(), quota.getSubscriptionId(),
                        ent.getCustomerId(), ent.getMsisdn(), type, threshold, remaining, total,
                        quota.getPeriodStart(), quota.getPeriodEnd()));
        log.info("quota: %{} esigi gecildi. sub={} type={} kalan={}",
                threshold, quota.getSubscriptionId(), type, remaining);
    }

    private Quota openQuota(SubscriptionEntitlement ent, LocalDate periodStart) {
        Quota quota = new Quota();
        quota.setSubscriptionId(ent.getSubscriptionId());
        quota.setPeriodStart(periodStart);
        quota.setPeriodEnd(periodStart.plusMonths(1));
        quota.setMinutesTotal(toDecimal(ent.getMinutesIncluded()));
        quota.setSmsTotal(toDecimal(ent.getSmsIncluded()));
        quota.setMbTotal(toDecimal(ent.getMbIncluded()));
        quota.setMinutesRemaining(quota.getMinutesTotal());
        quota.setSmsRemaining(quota.getSmsTotal());
        quota.setMbRemaining(quota.getMbTotal());
        quota.setCreatedAt(Instant.now());
        return quota;
    }

    /** Donem anahtari: verilen anin UTC takvim ayi baslangici (kota query'si de kullanir). */
    public static LocalDate periodStartOf(Instant at) {
        return at.atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
    }

    private static BigDecimal toDecimal(Integer value) {
        return value != null ? new BigDecimal(value) : null;
    }

    private static BigDecimal totalOf(Quota q, String type) {
        return switch (type) {
            case "VOICE" -> q.getMinutesTotal();
            case "SMS" -> q.getSmsTotal();
            case "DATA" -> q.getMbTotal();
            default -> null;
        };
    }

    private static BigDecimal remainingOf(Quota q, String type) {
        BigDecimal remaining = switch (type) {
            case "VOICE" -> q.getMinutesRemaining();
            case "SMS" -> q.getSmsRemaining();
            case "DATA" -> q.getMbRemaining();
            default -> null;
        };
        return remaining != null ? remaining : BigDecimal.ZERO;
    }

    private static void setRemaining(Quota q, String type, BigDecimal value) {
        switch (type) {
            case "VOICE" -> q.setMinutesRemaining(value);
            case "SMS" -> q.setSmsRemaining(value);
            case "DATA" -> q.setMbRemaining(value);
            default -> { }
        }
    }

    private static short warnedPctOf(Quota q, String type) {
        return switch (type) {
            case "VOICE" -> q.getMinutesWarnedPct();
            case "SMS" -> q.getSmsWarnedPct();
            case "DATA" -> q.getMbWarnedPct();
            default -> 0;
        };
    }

    private static void setWarnedPct(Quota q, String type, short pct) {
        switch (type) {
            case "VOICE" -> q.setMinutesWarnedPct(pct);
            case "SMS" -> q.setSmsWarnedPct(pct);
            case "DATA" -> q.setMbWarnedPct(pct);
            default -> { }
        }
    }
}
