package com.turkcell.commonlib.saga;

import java.util.UUID;

/**
 * Compensation komutu: order -> subscription.
 * Rezervasyonu geri al (RESERVED/ALLOCATED -> FREE), abonelik CANCELLED.
 */
public record ReleaseMsisdnCommand(
        UUID eventId,
        UUID orderId,
        String reason) {
}
