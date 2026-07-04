package com.turkcell.notificationservice.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.saga.TicketOpened;
import com.turkcell.notificationservice.entity.Notification;
import com.turkcell.notificationservice.entity.ProcessedEvent;
import com.turkcell.notificationservice.repository.NotificationRepository;
import com.turkcell.notificationservice.repository.ProcessedEventRepository;

/**
 * Destek talebi bildirimleri (G7, FR-33 / docx §8.9): talep acildiginda musteriye
 * "talebiniz alindi" SMS'i (mock). Inbox idempotency + yazim tek transaction.
 */
@Service
public class TicketEventHandler {

    private static final Logger log = LoggerFactory.getLogger(TicketEventHandler.class);
    private static final String TPL_OPENED = "TICKET_OPENED";

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationRepository notificationRepository;

    public TicketEventHandler(ProcessedEventRepository processedEventRepository,
                              NotificationRepository notificationRepository) {
        this.processedEventRepository = processedEventRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void handleOpened(TicketOpened event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("notification: event zaten islendi, atlaniyor. eventId={}", event.eventId());
            return;
        }

        Notification notification = new Notification();
        notification.setUserId(event.customerId());
        notification.setTemplateCode(TPL_OPENED);
        notification.setChannel("SMS");
        notification.setStatus("SENT");
        notification.setSentAt(Instant.now());
        notificationRepository.save(notification);

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(event.eventId());
        processed.setProcessedAt(Instant.now());
        processedEventRepository.save(processed);

        log.info("notification: {} gonderildi (SMS) -> customer={} ticket={} team={} slaDueAt={}",
                TPL_OPENED, event.customerId(), event.ticketId(), event.team(), event.slaDueAt());
    }
}
