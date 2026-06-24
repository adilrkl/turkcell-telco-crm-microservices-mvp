package com.turkcell.commonlib.saga;

import java.util.UUID;

/** Reply: subscription -> order. Aktivasyon basarisiz -> odeme iade + rezervasyon birakma (compensation). */
public record SubscriptionActivationFailed(
        UUID eventId,
        UUID orderId,
        String reason) {
}
