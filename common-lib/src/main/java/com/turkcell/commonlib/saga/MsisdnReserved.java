package com.turkcell.commonlib.saga;

import java.util.UUID;

/** Reply: subscription -> order. MSISDN rezerve edildi, PENDING abonelik olustu. */
public record MsisdnReserved(
        UUID eventId,
        UUID orderId,
        String msisdn) {
}
