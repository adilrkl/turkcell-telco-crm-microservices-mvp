package com.turkcell.commonlib.saga;

import java.util.UUID;

/**
 * Saga adim 3 komutu: order -> subscription.
 * Odeme tamamlandi; rezerve MSISDN'i ALLOCATED yap, SIM ata, abonelik ACTIVE.
 */
public record ActivateSubscriptionCommand(
        UUID eventId,
        UUID orderId) {
}
