package com.turkcell.commonlib.saga;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Domain event: order -> dis dunya ({@link SagaTopics#ORDER_EVENTS}).
 * Saga BASARIYLA tamamlandiginda (order FULFILLED) yayinlanir.
 * notification welcome mesaji atar, billing bill_cycle acar, usage tarife hakkindan
 * kota acar (saga DISI reaksiyon). Tarife hak alanlari (dakika/SMS/MB) event uzerinde
 * tasinir ki usage-service katalog'a senkron cagri yapmak zorunda kalmasin
 * (event-carried state transfer).
 */
public record OrderConfirmed(
        UUID eventId,
        UUID orderId,
        UUID customerId,
        UUID subscriptionId,
        String tariffCode,
        String msisdn,
        BigDecimal amount,
        String currency,
        Integer minutesIncluded,
        Integer smsIncluded,
        Integer dataMbIncluded) {
}
