package com.turkcell.commonlib.saga;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Saga adim 2 komutu: order -> payment.
 * Siparis tutarini tahsil et (onboarding tek seferlik ucret; aylik fatura DEGIL).
 */
public record ChargePaymentCommand(
        UUID eventId,
        UUID orderId,
        UUID customerId,
        BigDecimal amount,
        String currency) {
}
