package com.turkcell.notificationservice.consumer;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import com.turkcell.commonlib.saga.QuotaThresholdReached;
import com.turkcell.commonlib.saga.SagaHeaders;
import com.turkcell.notificationservice.service.QuotaEventHandler;

import tools.jackson.databind.ObjectMapper;

/**
 * quota-events topic tuketicisi. "QuotaThresholdReached" -> esik SMS'i.
 * "OverageRecorded" ayni topic'te akar ama billing'in isidir; burada atlanir.
 */
@Configuration
public class QuotaEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(QuotaEventConsumer.class);

    private final QuotaEventHandler handler;
    private final ObjectMapper objectMapper;

    public QuotaEventConsumer(QuotaEventHandler handler, ObjectMapper objectMapper) {
        this.handler = handler;
        this.objectMapper = objectMapper;
    }

    @Bean
    public Consumer<Message<byte[]>> consumeQuotaEvents() {
        return message -> {
            String type = eventType(message);
            if ("QuotaThresholdReached".equals(type)) {
                handler.handleThreshold(objectMapper.readValue(message.getPayload(), QuotaThresholdReached.class));
            } else {
                log.debug("notification: ilgisiz quota event atlandi: {}", type);
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
