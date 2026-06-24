package com.turkcell.commonlib.saga;

import java.util.UUID;

/** Reply: subscription -> order. Abonelik ACTIVE, MSISDN ALLOCATED, SIM atandi -> saga basariyla tamamlanir. */
public record SubscriptionActivated(
        UUID eventId,
        UUID orderId,
        UUID subscriptionId,
        String msisdn) {
}
