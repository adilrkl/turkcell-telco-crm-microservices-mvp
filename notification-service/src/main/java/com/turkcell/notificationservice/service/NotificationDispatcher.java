package com.turkcell.notificationservice.service;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.turkcell.notificationservice.entity.Notification;
import com.turkcell.notificationservice.entity.ProcessedEvent;
import com.turkcell.notificationservice.repository.NotificationRepository;
import com.turkcell.notificationservice.repository.ProcessedEventRepository;

/**
 * Tum bildirim gonderimlerinin ortak yolu: inbox idempotency + iletisim tercihi (FR-30) +
 * notification satiri. Cagiran handler'in transaction'i icinde calisir.
 *
 * <ul>
 *   <li>Ayni eventId daha once islendiyse atlanir (at-least-once teslimat).</li>
 *   <li>Kullanici kanaldan opt-out ise satir {@code SKIPPED} yazilir (gonderim yok), aksi halde {@code SENT}.</li>
 * </ul>
 * Her iki durumda da event processed islaretlenir (redelivery tekrar denemez).
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceService preferences;

    public NotificationDispatcher(ProcessedEventRepository processedEventRepository,
                                  NotificationRepository notificationRepository,
                                  NotificationPreferenceService preferences) {
        this.processedEventRepository = processedEventRepository;
        this.notificationRepository = notificationRepository;
        this.preferences = preferences;
    }

    public void dispatch(UUID eventId, UUID userId, String templateCode, String channel, String detail) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("notification: event zaten islendi, atlaniyor. eventId={}", eventId);
            return;
        }

        boolean allowed = preferences.isAllowed(userId, channel);

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTemplateCode(templateCode);
        notification.setChannel(channel);
        notification.setStatus(allowed ? "SENT" : "SKIPPED");
        if (allowed) {
            notification.setSentAt(Instant.now());
        }
        notificationRepository.save(notification);

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processed.setProcessedAt(Instant.now());
        processedEventRepository.save(processed);

        if (allowed) {
            log.info("notification: {} gonderildi ({}) -> user={} {}", templateCode, channel, userId, detail);
        } else {
            log.info("notification: {} atlandi (opt-out, {}) -> user={} {}", templateCode, channel, userId, detail);
        }
    }
}
