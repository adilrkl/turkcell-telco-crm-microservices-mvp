package com.turkcell.commonlib.saga;

import java.util.UUID;

/** Reply: payment -> order. Tahsilat basarili. */
public record PaymentCompleted(
        UUID eventId,
        UUID orderId,
        UUID paymentId) {
}
