package com.turkcell.commonlib.saga;

import java.util.UUID;

/** Reply (compensation ack): payment -> order. Odeme iade edildi. */
public record PaymentRefunded(
        UUID eventId,
        UUID orderId) {
}
