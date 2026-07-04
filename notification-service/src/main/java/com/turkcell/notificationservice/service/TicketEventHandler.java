package com.turkcell.notificationservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.saga.TicketOpened;

/**
 * Destek talebi bildirimleri (G7, FR-33 / docx §8.9): talep acildiginda musteriye
 * "talebiniz alindi" SMS'i (mock). Idempotency + opt-out (FR-30) {@link NotificationDispatcher}'da.
 */
@Service
public class TicketEventHandler {

    private static final String TPL_OPENED = "TICKET_OPENED";

    private final NotificationDispatcher dispatcher;

    public TicketEventHandler(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Transactional
    public void handleOpened(TicketOpened event) {
        dispatcher.dispatch(event.eventId(), event.customerId(), TPL_OPENED, "SMS",
                "ticket=" + event.ticketId() + " team=" + event.team() + " slaDueAt=" + event.slaDueAt());
    }
}
