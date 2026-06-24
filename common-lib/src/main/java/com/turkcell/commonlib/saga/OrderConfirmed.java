package com.turkcell.commonlib.saga;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Domain event: order -> dis dunya ({@link SagaTopics#ORDER_EVENTS}).
 * Saga BASARIYLA tamamlandiginda (order FULFILLED) yayinlanir.
 * notification welcome mesaji atar, billing bill_cycle acar (saga DISI reaksiyon).
 */
public record OrderConfirmed(
        UUID eventId,
        UUID orderId,
        UUID customerId,
        String tariffCode,
        String msisdn,
        BigDecimal amount,
        String currency) {
}
