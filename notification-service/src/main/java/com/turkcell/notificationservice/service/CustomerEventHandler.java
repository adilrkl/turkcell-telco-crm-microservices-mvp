package com.turkcell.notificationservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.saga.CustomerKYCApproved;

/**
 * Musteri yasam dongusu bildirimleri (G3, docx senaryo 14.1).
 * CustomerKYCApproved -> "KYC_APPROVED" (hesabiniz aktif, SMS mock).
 * Idempotency + opt-out (FR-30) {@link NotificationDispatcher}'da.
 */
@Service
public class CustomerEventHandler {

    private static final String TPL_KYC_APPROVED = "KYC_APPROVED";

    private final NotificationDispatcher dispatcher;

    public CustomerEventHandler(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Transactional
    public void handleKycApproved(CustomerKYCApproved event) {
        dispatcher.dispatch(event.eventId(), event.customerId(), TPL_KYC_APPROVED, "SMS",
                "hesap aktif (" + event.firstName() + " " + event.lastName() + ")");
    }
}
