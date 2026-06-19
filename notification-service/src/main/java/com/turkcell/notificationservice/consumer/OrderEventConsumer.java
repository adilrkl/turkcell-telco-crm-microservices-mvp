package com.turkcell.notificationservice.consumer;

import java.util.function.Consumer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.turkcell.commonlib.event.OrderPlacedEvent;
import com.turkcell.notificationservice.service.OrderEventHandler;

/**
 * Spring Cloud Stream functional consumer.
 * Bean adi = spring.cloud.function.definition (consumeOrderPlaced),
 * binding consumeOrderPlaced-in-0 -> order-events / group notification-service-group.
 */
@Configuration
public class OrderEventConsumer {

    @Bean
    public Consumer<OrderPlacedEvent> consumeOrderPlaced(OrderEventHandler handler) {
        return handler::handle;
    }
}
