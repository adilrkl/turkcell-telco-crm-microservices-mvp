package com.turkcell.commonlib.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * order-service tarafindan uretilen, "order-events" topic'i uzerinden
 * billing/notification gibi servislerce tuketilen domain event'i.
 * Producer ve consumer'lar TEK kontrat kullansin diye common-lib'te durur.
 * {@code eventId} inbox (idempotent consumer) icin benzersiz anahtardir.
 */
public record OrderPlacedEvent(
        UUID eventId,
        UUID orderId,
        UUID customerId,
        String tariffCode,
        BigDecimal amount,
        String currency,
        Instant occurredAt) {
}
