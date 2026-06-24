package com.turkcell.subscriptionservice.consumer;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import com.turkcell.commonlib.saga.ActivateSubscriptionCommand;
import com.turkcell.commonlib.saga.ReleaseMsisdnCommand;
import com.turkcell.commonlib.saga.ReserveMsisdnCommand;
import com.turkcell.commonlib.saga.SagaHeaders;
import com.turkcell.subscriptionservice.service.SubscriptionSagaService;

import tools.jackson.databind.ObjectMapper;

/**
 * subscription-commands topic tuketicisi (saga participant).
 * ReserveMsisdn / ActivateSubscription / ReleaseMsisdn komutlarini dispatch eder.
 */
@Configuration
public class SubscriptionCommandConsumer {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionCommandConsumer.class);

    private final SubscriptionSagaService service;
    private final ObjectMapper objectMapper;

    public SubscriptionCommandConsumer(SubscriptionSagaService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @Bean
    public Consumer<Message<byte[]>> subscriptionCommands() {
        return message -> {
            String type = eventType(message);
            byte[] body = message.getPayload();
            switch (type) {
                case "ReserveMsisdnCommand" -> service.reserve(objectMapper.readValue(body, ReserveMsisdnCommand.class));
                case "ActivateSubscriptionCommand" -> service.activate(objectMapper.readValue(body, ActivateSubscriptionCommand.class));
                case "ReleaseMsisdnCommand" -> service.release(objectMapper.readValue(body, ReleaseMsisdnCommand.class));
                default -> log.warn("Bilinmeyen subscription komut tipi: {}", type);
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
