package com.turkcell.notificationservice.consumer;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import com.turkcell.commonlib.saga.SagaHeaders;
import com.turkcell.commonlib.saga.TicketOpened;
import com.turkcell.notificationservice.service.TicketEventHandler;

import tools.jackson.databind.ObjectMapper;

/**
 * ticket-events topic tuketicisi (G7, FR-33): TicketOpened -> "talebiniz alindi" SMS'i.
 */
@Configuration
public class TicketEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TicketEventConsumer.class);

    private final TicketEventHandler handler;
    private final ObjectMapper objectMapper;

    public TicketEventConsumer(TicketEventHandler handler, ObjectMapper objectMapper) {
        this.handler = handler;
        this.objectMapper = objectMapper;
    }

    @Bean
    public Consumer<Message<byte[]>> consumeTicketEvents() {
        return message -> {
            String type = eventType(message);
            if ("TicketOpened".equals(type)) {
                handler.handleOpened(objectMapper.readValue(message.getPayload(), TicketOpened.class));
            } else {
                log.debug("notification: ilgisiz ticket event atlandi: {}", type);
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
