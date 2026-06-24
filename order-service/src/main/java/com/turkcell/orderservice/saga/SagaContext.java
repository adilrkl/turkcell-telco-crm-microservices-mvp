package com.turkcell.orderservice.saga;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * saga_states.payload icinde JSON olarak saklanan birikmis saga baglami.
 * Reply event'leri tum veriyi tasimaz (orn. MsisdnReserved tutari bilmez);
 * orchestrator sonraki komutu kurarken bu baglamdan okur.
 */
public record SagaContext(
        UUID customerId,
        String tariffCode,
        BigDecimal amount,
        String currency,
        String msisdn,
        UUID paymentId,
        UUID subscriptionId) {

    public SagaContext withMsisdn(String msisdn) {
        return new SagaContext(customerId, tariffCode, amount, currency, msisdn, paymentId, subscriptionId);
    }

    public SagaContext withPaymentId(UUID paymentId) {
        return new SagaContext(customerId, tariffCode, amount, currency, msisdn, paymentId, subscriptionId);
    }

    public SagaContext withSubscriptionId(UUID subscriptionId) {
        return new SagaContext(customerId, tariffCode, amount, currency, msisdn, paymentId, subscriptionId);
    }
}
