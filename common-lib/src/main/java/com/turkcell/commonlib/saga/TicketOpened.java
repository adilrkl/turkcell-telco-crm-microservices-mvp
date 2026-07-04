package com.turkcell.commonlib.saga;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event: ticket -> dis dunya ({@link SagaTopics#TICKET_EVENTS}).
 * Yeni destek talebi acildiginda yayinlanir (G7, FR-32/33 / docx §8.9);
 * notification "talebiniz alindi" SMS'i atar. Alanlar event-carried state:
 * notification'in mesaji kurmasi icin category/priority/team/slaDueAt tasinir
 * (consumer'da senkron cagri yok).
 */
public record TicketOpened(
        UUID eventId,
        UUID ticketId,
        UUID customerId,
        String category,
        String priority,
        String team,
        Instant slaDueAt) {
}
