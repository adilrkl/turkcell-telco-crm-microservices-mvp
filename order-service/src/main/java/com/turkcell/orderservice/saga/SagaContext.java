package com.turkcell.orderservice.saga;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * saga_states.payload icinde JSON olarak saklanan birikmis saga baglami.
 * Reply event'leri tum veriyi tasimaz (orn. MsisdnReserved tutari bilmez);
 * orchestrator sonraki komutu kurarken bu baglamdan okur.
 * Tarife hak alanlari (dakika/SMS/MB) siparis aninda katalogdan okunur ve
 * saga sonunda OrderConfirmed uzerinde usage-service'e tasinir (kota acilisi).
 * Eski saga_states satirlarinda bu alanlar yoktur; JSON'dan null okunur (sorun degil).
 */
public record SagaContext(
        UUID customerId,
        String tariffCode,
        BigDecimal amount,
        String currency,
        String msisdn,
        UUID paymentId,
        UUID subscriptionId,
        Integer minutesIncluded,
        Integer smsIncluded,
        Integer dataMbIncluded) {

    public SagaContext withMsisdn(String msisdn) {
        return new SagaContext(customerId, tariffCode, amount, currency, msisdn, paymentId, subscriptionId,
                minutesIncluded, smsIncluded, dataMbIncluded);
    }

    public SagaContext withPaymentId(UUID paymentId) {
        return new SagaContext(customerId, tariffCode, amount, currency, msisdn, paymentId, subscriptionId,
                minutesIncluded, smsIncluded, dataMbIncluded);
    }

    public SagaContext withSubscriptionId(UUID subscriptionId) {
        return new SagaContext(customerId, tariffCode, amount, currency, msisdn, paymentId, subscriptionId,
                minutesIncluded, smsIncluded, dataMbIncluded);
    }
}
