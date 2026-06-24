package com.turkcell.commonlib.saga;

import java.util.UUID;

/**
 * Compensation komutu: order -> payment.
 * Tahsil edilmis odemeyi iade et (status -> REFUNDED).
 */
public record RefundPaymentCommand(
        UUID eventId,
        UUID orderId,
        String reason) {
}
