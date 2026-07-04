package com.turkcell.notificationservice.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.saga.CustomerKYCApproved;
import com.turkcell.notificationservice.entity.Notification;
import com.turkcell.notificationservice.entity.ProcessedEvent;
import com.turkcell.notificationservice.repository.NotificationRepository;
import com.turkcell.notificationservice.repository.ProcessedEventRepository;

/**
 * Musteri yasam dongusu bildirimleri (G3, docx senaryo 14.1).
 * CustomerKYCApproved -> "KYC_APPROVED" (hesabiniz aktif, SMS mock).
 * Inbox idempotency + yazim tek transaction.
 */
@Service
public class CustomerEventHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomerEventHandler.class);
    private static final String TPL_KYC_APPROVED = "KYC_APPROVED";

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationRepository notificationRepository;

    public CustomerEventHandler(ProcessedEventRepository processedEventRepository,
                                NotificationRepository notificationRepository) {
        this.processedEventRepository = processedEventRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void handleKycApproved(CustomerKYCApproved event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("notification: event zaten islendi, atlaniyor. eventId={}", event.eventId());
            return;
        }

        Notification notification = new Notification();
        notification.setUserId(event.customerId());
        notification.setTemplateCode(TPL_KYC_APPROVED);
        notification.setChannel("SMS");
        notification.setStatus("SENT");
        notification.setSentAt(Instant.now());
        notificationRepository.save(notification);

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(event.eventId());
        processed.setProcessedAt(Instant.now());
        processedEventRepository.save(processed);

        log.info("notification: {} gonderildi (SMS) -> customer={} ({} {})",
                TPL_KYC_APPROVED, event.customerId(), event.firstName(), event.lastName());
    }
}
