package com.turkcell.orderservice.application.features.order.rule;

import org.springframework.stereotype.Component;

import com.turkcell.orderservice.client.dto.CustomerEnvelope.CustomerView;
import com.turkcell.orderservice.entity.Order;
import com.turkcell.orderservice.exception.InvalidOrderException;
import com.turkcell.orderservice.saga.OrderStatus;

/** Siparis is kurallari (alma + iptal). */
@Component
public class OrderBusinessRules {

    /** Yalnizca ACTIVE musteri siparis verebilir. */
    public void customerMustBeActive(CustomerView customer) {
        if (!"ACTIVE".equalsIgnoreCase(customer.status())) {
            throw new InvalidOrderException("Musteri aktif degil (status=" + customer.status() + ")");
        }
    }

    /** Yalniz terminal-oncesi siparis iptal edilebilir (G5): FULFILLED/CANCELLED reddedilir. */
    public void orderMustBeCancellable(Order order) {
        if (OrderStatus.FULFILLED.equals(order.getStatus()) || OrderStatus.CANCELLED.equals(order.getStatus())) {
            throw new InvalidOrderException(
                    "Siparis terminal durumda, iptal edilemez (status=" + order.getStatus() + ")");
        }
    }
}
