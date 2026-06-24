package com.turkcell.commonlib.saga;

import java.util.UUID;

/**
 * Saga adim 1 komutu: order -> subscription.
 * MSISDN havuzundan numara rezerve et ve PENDING abonelik olustur.
 * {@code eventId} consumer idempotency (inbox) anahtaridir; {@code orderId} saga korelasyonudur.
 */
public record ReserveMsisdnCommand(
        UUID eventId,
        UUID orderId,
        UUID customerId,
        String tariffCode) {
}
