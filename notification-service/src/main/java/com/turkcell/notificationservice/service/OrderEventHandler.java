package com.turkcell.notificationservice.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.event.OrderPlacedEvent;
import com.turkcell.notificationservice.entity.Notification;
import com.turkcell.notificationservice.entity.ProcessedEvent;
import com.turkcell.notificationservice.repository.NotificationRepository;
import com.turkcell.notificationservice.repository.ProcessedEventRepository;

/** OrderPlaced -> "ORDER_CONFIRMED" bildirimi. Inbox idempotency + yazim tek transaction. */
@Service
public class OrderEventHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderEventHandler.class);
    private static final String TEMPLATE_CODE = "ORDER_CONFIRMED";

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationRepository notificationRepository;

    public OrderEventHandler(ProcessedEventRepository processedEventRepository,
                             NotificationRepository notificationRepository) {
        this.processedEventRepository = processedEventRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void handle(OrderPlacedEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("notification: event zaten islendi, atlaniyor. eventId={}", event.eventId());
            return;
        }

        Notification notification = new Notification();
        notification.setUserId(event.customerId());
        notification.setTemplateCode(TEMPLATE_CODE);
        notification.setChannel("SMS");
        notification.setStatus("SENT");
        notification.setSentAt(Instant.now());
        notificationRepository.save(notification);

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(event.eventId());
        processed.setProcessedAt(Instant.now());
        processedEventRepository.save(processed);

        log.info("notification: ORDER_CONFIRMED gonderildi (SMS) -> customer={} order={}",
                event.customerId(), event.orderId());
    }
}
