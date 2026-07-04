package com.turkcell.notificationservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.saga.SubscriptionReactivated;
import com.turkcell.commonlib.saga.SubscriptionSuspended;
import com.turkcell.commonlib.saga.SubscriptionTerminated;

/**
 * Abonelik yasam dongusu bildirimleri (G4, FR-14 / docx §8.4), kanal SMS (mock).
 * Idempotency + opt-out (FR-30) {@link NotificationDispatcher}'da.
 *  - SubscriptionSuspended   -> "SUBSCRIPTION_SUSPENDED"   (hattiniz askiya alindi)
 *  - SubscriptionReactivated -> "SUBSCRIPTION_REACTIVATED" (hattiniz yeniden acildi)
 *  - SubscriptionTerminated  -> "SUBSCRIPTION_TERMINATED"  (hattiniz kapatildi)
 */
@Service
public class SubscriptionEventHandler {

    private static final String TPL_SUSPENDED = "SUBSCRIPTION_SUSPENDED";
    private static final String TPL_REACTIVATED = "SUBSCRIPTION_REACTIVATED";
    private static final String TPL_TERMINATED = "SUBSCRIPTION_TERMINATED";

    private final NotificationDispatcher dispatcher;

    public SubscriptionEventHandler(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Transactional
    public void handleSuspended(SubscriptionSuspended event) {
        dispatcher.dispatch(event.eventId(), event.customerId(), TPL_SUSPENDED, "SMS",
                "msisdn=" + event.msisdn() + " sebep=" + event.reason());
    }

    @Transactional
    public void handleReactivated(SubscriptionReactivated event) {
        dispatcher.dispatch(event.eventId(), event.customerId(), TPL_REACTIVATED, "SMS",
                "msisdn=" + event.msisdn());
    }

    @Transactional
    public void handleTerminated(SubscriptionTerminated event) {
        dispatcher.dispatch(event.eventId(), event.customerId(), TPL_TERMINATED, "SMS",
                "msisdn=" + event.msisdn() + " sebep=" + event.reason());
    }
}
