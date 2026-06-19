package com.turkcell.billingservice.consumer;

import java.util.function.Consumer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.turkcell.billingservice.service.OrderEventHandler;
import com.turkcell.commonlib.event.OrderPlacedEvent;

/**
 * Spring Cloud Stream functional consumer.
 * Bean adi = spring.cloud.function.definition (consumeOrderPlaced),
 * binding consumeOrderPlaced-in-0 -> order-events / group billing-service-group.
 */
@Configuration
public class OrderEventConsumer {

    @Bean
    public Consumer<OrderPlacedEvent> consumeOrderPlaced(OrderEventHandler handler) {
        return handler::handle;
    }
}
