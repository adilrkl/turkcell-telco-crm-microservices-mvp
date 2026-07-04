package com.turkcell.notificationservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.saga.OrderCancelled;
import com.turkcell.commonlib.saga.OrderConfirmed;

/**
 * Order domain event'lerinden bildirim uretir (kanal SMS). Idempotency + opt-out (FR-30)
 * {@link NotificationDispatcher}'da; burada yalniz template + detay kurulur.
 *  - OrderConfirmed -> "ORDER_CONFIRMED" (welcome)
 *  - OrderCancelled -> "ORDER_CANCELLED" (basarisizlik)
 */
@Service
public class OrderEventHandler {

    private static final String TPL_CONFIRMED = "ORDER_CONFIRMED";
    private static final String TPL_CANCELLED = "ORDER_CANCELLED";

    private final NotificationDispatcher dispatcher;

    public OrderEventHandler(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Transactional
    public void handleConfirmed(OrderConfirmed event) {
        dispatcher.dispatch(event.eventId(), event.customerId(), TPL_CONFIRMED, "SMS",
                "welcome (order=" + event.orderId() + ", msisdn=" + event.msisdn() + ")");
    }

    @Transactional
    public void handleCancelled(OrderCancelled event) {
        dispatcher.dispatch(event.eventId(), event.customerId(), TPL_CANCELLED, "SMS",
                "iptal (order=" + event.orderId() + ", sebep=" + event.reason() + ")");
    }
}
