package com.turkcell.billingservice.consumer;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import com.turkcell.billingservice.service.OrderEventHandler;
import com.turkcell.commonlib.saga.OrderConfirmed;
import com.turkcell.commonlib.saga.SagaHeaders;

import tools.jackson.databind.ObjectMapper;

/**
 * order-events topic tuketicisi (saga DISI reaksiyon). Saga BASARIYLA bittiginde
 * order 'OrderConfirmed' yayinlar; billing bunun uzerine fatura dongusu (bill_cycle) acar.
 * Payload ham JSON byte; {@code eventType} header'ina gore dispatch edilir.
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
                handler.handle(objectMapper.readValue(message.getPayload(), OrderConfirmed.class));
            } else {
                log.debug("billing: ilgisiz order event atlandi: {}", type);
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
