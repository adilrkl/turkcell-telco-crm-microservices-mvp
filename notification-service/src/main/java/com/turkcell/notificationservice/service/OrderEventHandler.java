package com.turkcell.notificationservice.service;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.saga.OrderCancelled;
import com.turkcell.commonlib.saga.OrderConfirmed;
import com.turkcell.notificationservice.entity.Notification;
import com.turkcell.notificationservice.entity.ProcessedEvent;
import com.turkcell.notificationservice.repository.NotificationRepository;
import com.turkcell.notificationservice.repository.ProcessedEventRepository;

/**
 * Order domain event'lerinden bildirim uretir. Inbox idempotency + yazim tek transaction.
 *  - OrderConfirmed -> "ORDER_CONFIRMED" (welcome)
 *  - OrderCancelled -> "ORDER_CANCELLED" (basarisizlik)
 */
@Service
public class OrderEventHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderEventHandler.class);
    private static final String TPL_CONFIRMED = "ORDER_CONFIRMED";
    private static final String TPL_CANCELLED = "ORDER_CANCELLED";

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationRepository notificationRepository;

    public OrderEventHandler(ProcessedEventRepository processedEventRepository,
                             NotificationRepository notificationRepository) {
        this.processedEventRepository = processedEventRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void handleConfirmed(OrderConfirmed event) {
        send(event.eventId(), event.customerId(), TPL_CONFIRMED,
                "welcome (order=" + event.orderId() + ", msisdn=" + event.msisdn() + ")");
    }

    @Transactional
    public void handleCancelled(OrderCancelled event) {
        send(event.eventId(), event.customerId(), TPL_CANCELLED,
                "iptal (order=" + event.orderId() + ", sebep=" + event.reason() + ")");
    }

    private void send(UUID eventId, UUID customerId, String templateCode, String detail) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("notification: event zaten islendi, atlaniyor. eventId={}", eventId);
            return;
        }

        Notification notification = new Notification();
        notification.setUserId(customerId);
        notification.setTemplateCode(templateCode);
        notification.setChannel("SMS");
        notification.setStatus("SENT");
        notification.setSentAt(Instant.now());
        notificationRepository.save(notification);

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processed.setProcessedAt(Instant.now());
        processedEventRepository.save(processed);

        log.info("notification: {} gonderildi (SMS) -> customer={} {}", templateCode, customerId, detail);
    }
}
