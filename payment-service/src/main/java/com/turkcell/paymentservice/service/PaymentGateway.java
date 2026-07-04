package com.turkcell.paymentservice.service;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Mock PSP (odeme saglayici) karari. DEMO knob: tutar esigi asilirsa tahsilat REDDEDILIR.
 * Tek kaynak: hem saga tahsilati hem recurring fatura tahsilati hem dunning retry buradan sorar.
 */
@Component
public class PaymentGateway {

    private final BigDecimal failThreshold;

    public PaymentGateway(@Value("${payment.fail-threshold:1000}") BigDecimal failThreshold) {
        this.failThreshold = failThreshold;
    }

    /** Tutar esigin altinda/esitse onaylanir; ustundeyse reddedilir. */
    public boolean authorize(BigDecimal amount) {
        return amount.compareTo(failThreshold) <= 0;
    }
}
