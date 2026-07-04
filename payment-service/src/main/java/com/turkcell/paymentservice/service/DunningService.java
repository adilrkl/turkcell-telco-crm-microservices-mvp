package com.turkcell.paymentservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.saga.InvoicePaid;
import com.turkcell.commonlib.saga.SagaTopics;
import com.turkcell.paymentservice.config.DunningProperties;
import com.turkcell.paymentservice.entity.DunningSchedule;
import com.turkcell.paymentservice.entity.DunningStatus;
import com.turkcell.paymentservice.repository.DunningScheduleRepository;
import com.turkcell.paymentservice.saga.OutboxWriter;

/**
 * Dunning retry (G8, FR-27): basarisiz bir fatura tahsilati icin yeniden deneme plani acar ve
 * vadesi gelen planlari periyodik re-charge eder. Retry basarili olursa InvoicePaid yayinlanir
 * (billing fatura PAID'e doner, notification "odendi" bildirimi atar); tum retry'lar tukenirse
 * plan EXHAUSTED olur. Offset'ler {@link DunningProperties#getIntervals()} (24/72/168 saat).
 */
@Service
public class DunningService {

    private static final Logger log = LoggerFactory.getLogger(DunningService.class);
    private static final String RETRY_METHOD = "AUTO_PAY_RETRY";

    private final DunningScheduleRepository scheduleRepository;
    private final InvoiceChargeProcessor invoiceCharge;
    private final OutboxWriter outbox;
    private final PaymentAuditWriter audit;
    private final DunningProperties properties;

    public DunningService(DunningScheduleRepository scheduleRepository,
                          InvoiceChargeProcessor invoiceCharge,
                          OutboxWriter outbox,
                          PaymentAuditWriter audit,
                          DunningProperties properties) {
        this.scheduleRepository = scheduleRepository;
        this.invoiceCharge = invoiceCharge;
        this.outbox = outbox;
        this.audit = audit;
        this.properties = properties;
    }

    /**
     * Ilk fatura tahsilati basarisiz olunca cagrilir (chargeInvoice'in transaction'i icinde):
     * ilk retry offset[0] sonrasina planlanir. Ayni fatura icin plan varsa tekrar acilmaz.
     */
    public void open(UUID invoiceId, UUID customerId, BigDecimal amount, String currency,
                     String error, Instant failedAt) {
        List<java.time.Duration> intervals = properties.getIntervals();
        if (intervals.isEmpty()) {
            log.warn("dunning: interval tanimsiz, retry planlanmadi. invoice={}", invoiceId);
            return;
        }
        if (scheduleRepository.findByInvoiceId(invoiceId).isPresent()) {
            log.info("dunning: invoice={} icin plan zaten var, atlaniyor", invoiceId);
            return;
        }

        DunningSchedule schedule = new DunningSchedule();
        schedule.setInvoiceId(invoiceId);
        schedule.setCustomerId(customerId);
        schedule.setAmount(amount);
        schedule.setCurrency(currency);
        schedule.setRetryCount(0);
        schedule.setMaxRetries(intervals.size());
        schedule.setOriginFailedAt(failedAt);
        schedule.setNextRetryAt(failedAt.plus(intervals.get(0)));
        schedule.setStatus(DunningStatus.PENDING);
        schedule.setLastError(error);
        schedule.setCreatedAt(Instant.now());
        scheduleRepository.save(schedule);

        audit.write("DunningSchedule", schedule.getId(), "DUNNING_OPENED",
                "invoice=" + invoiceId + " nextRetryAt=" + schedule.getNextRetryAt()
                        + " maxRetries=" + schedule.getMaxRetries());
        log.info("dunning: plan acildi invoice={} ilk retry={} (maks {} deneme)",
                invoiceId, schedule.getNextRetryAt(), schedule.getMaxRetries());
    }

    /** Vadesi gelen dunning planlarini re-charge eder. */
    @Scheduled(fixedDelayString = "${payment.dunning.poll-interval-ms:60000}")
    @Transactional
    public void retryDue() {
        List<DunningSchedule> due = scheduleRepository.findDue(Instant.now(), 50);
        for (DunningSchedule schedule : due) {
            attemptRetry(schedule);
        }
    }

    private void attemptRetry(DunningSchedule schedule) {
        InvoiceChargeProcessor.Outcome outcome = invoiceCharge.charge(
                schedule.getInvoiceId(), schedule.getCustomerId(), schedule.getAmount(),
                schedule.getCurrency(), RETRY_METHOD, properties.isDemoRecoverOnRetry());

        if (outcome.approved()) {
            resolve(schedule, outcome.paymentId());
        } else {
            advanceOrExhaust(schedule);
        }
        schedule.setUpdatedAt(Instant.now());
        scheduleRepository.save(schedule);
    }

    private void resolve(DunningSchedule schedule, UUID paymentId) {
        outbox.enqueue(SagaTopics.INVOICE_EVENTS, "InvoicePaid", schedule.getInvoiceId(),
                new InvoicePaid(UUID.randomUUID(), schedule.getInvoiceId(), paymentId,
                        schedule.getCustomerId(), schedule.getAmount(), schedule.getCurrency()));
        schedule.setStatus(DunningStatus.RESOLVED);
        audit.write("DunningSchedule", schedule.getId(), "DUNNING_RESOLVED",
                "invoice=" + schedule.getInvoiceId() + " retry=" + (schedule.getRetryCount() + 1)
                        + " payment=" + paymentId);
        log.info("dunning: invoice={} {}. retry'de tahsil edildi -> RESOLVED (InvoicePaid)",
                schedule.getInvoiceId(), schedule.getRetryCount() + 1);
    }

    private void advanceOrExhaust(DunningSchedule schedule) {
        int done = schedule.getRetryCount() + 1;
        schedule.setRetryCount(done);
        schedule.setLastError("retry " + done + " reddedildi (tutar=" + schedule.getAmount() + ")");
        if (done >= schedule.getMaxRetries()) {
            schedule.setStatus(DunningStatus.EXHAUSTED);
            audit.write("DunningSchedule", schedule.getId(), "DUNNING_EXHAUSTED",
                    "invoice=" + schedule.getInvoiceId() + " denemeler tukendi (" + done + ")");
            log.warn("dunning: invoice={} {} deneme sonrasi tahsil edilemedi -> EXHAUSTED",
                    schedule.getInvoiceId(), done);
        } else {
            schedule.setNextRetryAt(schedule.getOriginFailedAt().plus(properties.getIntervals().get(done)));
            log.info("dunning: invoice={} retry {} basarisiz, sonraki deneme={}",
                    schedule.getInvoiceId(), done, schedule.getNextRetryAt());
        }
    }
}
