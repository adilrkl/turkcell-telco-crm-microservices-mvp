package com.turkcell.notificationservice.consumer;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import com.turkcell.commonlib.saga.CustomerKYCApproved;
import com.turkcell.commonlib.saga.SagaHeaders;
import com.turkcell.notificationservice.service.CustomerEventHandler;

import tools.jackson.databind.ObjectMapper;

/** customer-events topic tuketicisi. "CustomerKYCApproved" -> hesap aktif SMS'i. */
@Configuration
public class CustomerEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(CustomerEventConsumer.class);

    private final CustomerEventHandler handler;
    private final ObjectMapper objectMapper;

    public CustomerEventConsumer(CustomerEventHandler handler, ObjectMapper objectMapper) {
        this.handler = handler;
        this.objectMapper = objectMapper;
    }

    @Bean
    public Consumer<Message<byte[]>> consumeCustomerEvents() {
        return message -> {
            String type = eventType(message);
            if ("CustomerKYCApproved".equals(type)) {
                handler.handleKycApproved(objectMapper.readValue(message.getPayload(), CustomerKYCApproved.class));
            } else {
                log.debug("notification: ilgisiz customer event atlandi: {}", type);
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
