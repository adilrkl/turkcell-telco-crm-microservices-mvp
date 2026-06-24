package com.turkcell.commonlib.saga;

import java.util.UUID;

/** Reply: subscription -> order. Rezervasyon basarisiz (orn. havuzda bos numara yok). Henuz compensation gerekmez. */
public record MsisdnReservationFailed(
        UUID eventId,
        UUID orderId,
        String reason) {
}
