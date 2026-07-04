package com.turkcell.billingservice.consumer;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import com.turkcell.billingservice.service.OverageEventHandler;
import com.turkcell.commonlib.saga.OverageRecorded;
import com.turkcell.commonlib.saga.SagaHeaders;

import tools.jackson.databind.ObjectMapper;

/**
 * quota-events topic tuketicisi. "OverageRecorded" -> bekleyen asim ucreti.
 * "QuotaThresholdReached" ayni topic'te akar ama notification'in isidir; burada atlanir.
 */
@Configuration
public class QuotaEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(QuotaEventConsumer.class);

    private final OverageEventHandler handler;
    private final ObjectMapper objectMapper;

    public QuotaEventConsumer(OverageEventHandler handler, ObjectMapper objectMapper) {
        this.handler = handler;
        this.objectMapper = objectMapper;
    }

    @Bean
    public Consumer<Message<byte[]>> consumeQuotaEvents() {
        return message -> {
            String type = eventType(message);
            if ("OverageRecorded".equals(type)) {
                handler.handle(objectMapper.readValue(message.getPayload(), OverageRecorded.class));
            } else {
                log.debug("billing: ilgisiz quota event atlandi: {}", type);
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
