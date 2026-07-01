package com.turkcell.orderservice.application.features.order.rule;

import org.springframework.stereotype.Component;

import com.turkcell.orderservice.client.dto.CustomerEnvelope.CustomerView;
import com.turkcell.orderservice.exception.InvalidOrderException;

/** Siparis alma is kurallari. */
@Component
public class OrderBusinessRules {

    /** Yalnizca ACTIVE musteri siparis verebilir. */
    public void customerMustBeActive(CustomerView customer) {
        if (!"ACTIVE".equalsIgnoreCase(customer.status())) {
            throw new InvalidOrderException("Musteri aktif degil (status=" + customer.status() + ")");
        }
    }
}
