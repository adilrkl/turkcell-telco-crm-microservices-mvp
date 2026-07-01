package com.turkcell.orderservice.application.features.order.mapper;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.turkcell.orderservice.application.features.order.command.place.PlaceOrderCommand;
import com.turkcell.orderservice.client.dto.TariffEnvelope.TariffView;
import com.turkcell.orderservice.dto.OrderResponse;
import com.turkcell.orderservice.entity.Order;
import com.turkcell.orderservice.entity.OrderItem;
import com.turkcell.orderservice.saga.OrderStatus;

/** Order entity <-> command/response donusumleri. */
@Component
public class OrderMapper {

    private static final String PRODUCT_TYPE_TARIFF = "TARIFF";

    /** Komuttan + cekilen tarifeden PENDING_PAYMENT durumunda yeni siparis (+ kalem) olusturur. */
    public Order toPendingOrder(PlaceOrderCommand command, TariffView tariff, BigDecimal price, String currency) {
        Order order = new Order();
        order.setCustomerId(command.customerId());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(price);
        order.setCurrency(currency);
        order.setCreatedAt(Instant.now());

        OrderItem item = new OrderItem();
        item.setProductCode(tariff.code());
        item.setProductType(PRODUCT_TYPE_TARIFF);
        item.setQuantity(1);
        item.setUnitPrice(price);
        order.addItem(item);
        return order;
    }

    public OrderResponse toResponse(Order order, String tariffCode) {
        return new OrderResponse(order.getId(), order.getCustomerId(), order.getStatus(),
                order.getTotalAmount(), order.getCurrency(), tariffCode);
    }
}
