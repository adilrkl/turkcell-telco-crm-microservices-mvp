package com.turkcell.commonlib.saga;

import java.util.UUID;

/** Reply (compensation ack): subscription -> order. Rezervasyon/abonelik geri alindi. */
public record MsisdnReleased(
        UUID eventId,
        UUID orderId) {
}
