package com.turkcell.notificationservice.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.saga.QuotaThresholdReached;
import com.turkcell.notificationservice.entity.Notification;
import com.turkcell.notificationservice.entity.ProcessedEvent;
import com.turkcell.notificationservice.repository.NotificationRepository;
import com.turkcell.notificationservice.repository.ProcessedEventRepository;

/**
 * Kota esik event'lerinden SMS bildirimi uretir (G1, docx senaryo 14.3).
 * Inbox idempotency + yazim tek transaction.
 *  - %80  -> "QUOTA_WARNING_80"  (kotaniz azaliyor)
 *  - %100 -> "QUOTA_EXCEEDED"   (kotaniz bitti; asim ucretlendirilir)
 */
@Service
public class QuotaEventHandler {

    private static final Logger log = LoggerFactory.getLogger(QuotaEventHandler.class);
    private static final String TPL_WARNING_80 = "QUOTA_WARNING_80";
    private static final String TPL_EXCEEDED = "QUOTA_EXCEEDED";

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationRepository notificationRepository;

    public QuotaEventHandler(ProcessedEventRepository processedEventRepository,
                             NotificationRepository notificationRepository) {
        this.processedEventRepository = processedEventRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void handleThreshold(QuotaThresholdReached event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("notification: event zaten islendi, atlaniyor. eventId={}", event.eventId());
            return;
        }

        String templateCode = event.thresholdPct() >= 100 ? TPL_EXCEEDED : TPL_WARNING_80;

        Notification notification = new Notification();
        notification.setUserId(event.customerId());
        notification.setTemplateCode(templateCode);
        notification.setChannel("SMS");
        notification.setStatus("SENT");
        notification.setSentAt(Instant.now());
        notificationRepository.save(notification);

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(event.eventId());
        processed.setProcessedAt(Instant.now());
        processedEventRepository.save(processed);

        log.info("notification: {} gonderildi (SMS) -> customer={} (sub={} type={} %{} kalan={}/{} msisdn={})",
                templateCode, event.customerId(), event.subscriptionId(), event.type(),
                event.thresholdPct(), event.remaining(), event.total(), event.msisdn());
    }
}
