package com.turkcell.usageservice.consumer;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import com.turkcell.commonlib.saga.OrderConfirmed;
import com.turkcell.commonlib.saga.SagaHeaders;
import com.turkcell.usageservice.service.OrderEventHandler;

import tools.jackson.databind.ObjectMapper;

/**
 * order-events topic tuketicisi. "OrderConfirmed" event'inden tarife hak fotografi
 * yazilir ve donem kotasi acilir (inbox idempotency ile).
 */
@Configuration
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final OrderEventHandler handler;
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(OrderEventHandler handler, ObjectMapper objectMapper) {
        this.handler = handler;
        this.objectMapper = objectMapper;
    }

    @Bean
    public Consumer<Message<byte[]>> consumeOrderEvents() {
        return message -> {
            String type = eventType(message);
            if ("OrderConfirmed".equals(type)) {
                handler.handleConfirmed(objectMapper.readValue(message.getPayload(), OrderConfirmed.class));
            } else {
                log.debug("usage: ilgisiz order event atlandi: {}", type);
            }
        };
    }

    private static String eventType(Message<byte[]> message) {
        Object raw = message.getHeaders().get(SagaHeaders.EVENT_TYPE);
        if (raw instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(raw);
    }
}
