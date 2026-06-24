package com.turkcell.commonlib.saga;

import java.util.UUID;

/** Reply: payment -> order. Tahsilat basarisiz -> MSISDN rezervasyonu birakilmali (compensation). */
public record PaymentFailed(
        UUID eventId,
        UUID orderId,
        String reason) {
}
