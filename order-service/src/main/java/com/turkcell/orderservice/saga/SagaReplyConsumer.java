package com.turkcell.orderservice.saga;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import com.turkcell.commonlib.saga.MsisdnReleased;
import com.turkcell.commonlib.saga.MsisdnReservationFailed;
import com.turkcell.commonlib.saga.MsisdnReserved;
import com.turkcell.commonlib.saga.PaymentCompleted;
import com.turkcell.commonlib.saga.PaymentFailed;
import com.turkcell.commonlib.saga.PaymentRefunded;
import com.turkcell.commonlib.saga.SagaHeaders;
import com.turkcell.commonlib.saga.SubscriptionActivated;
import com.turkcell.commonlib.saga.SubscriptionActivationFailed;

import tools.jackson.databind.ObjectMapper;

/**
 * saga-replies topic'inden gelen participant reply'larini tuketir.
 * Payload ham JSON byte; {@code eventType} header'ina gore dogru tipe deserialize edip
 * orchestrator'a dispatch eder (functional Spring Cloud Stream consumer).
 */
@Configuration
public class SagaReplyConsumer {

    private static final Logger log = LoggerFactory.getLogger(SagaReplyConsumer.class);

    private final OrderSagaOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

    public SagaReplyConsumer(OrderSagaOrchestrator orchestrator, ObjectMapper objectMapper) {
        this.orchestrator = orchestrator;
        this.objectMapper = objectMapper;
    }

    @Bean
    public Consumer<Message<byte[]>> sagaReplies() {
        return message -> {
            String type = eventType(message);
            byte[] body = message.getPayload();
            switch (type) {
                case "MsisdnReserved" -> orchestrator.onMsisdnReserved(objectMapper.readValue(body, MsisdnReserved.class));
                case "MsisdnReservationFailed" -> orchestrator.onMsisdnReservationFailed(objectMapper.readValue(body, MsisdnReservationFailed.class));
                case "PaymentCompleted" -> orchestrator.onPaymentCompleted(objectMapper.readValue(body, PaymentCompleted.class));
                case "PaymentFailed" -> orchestrator.onPaymentFailed(objectMapper.readValue(body, PaymentFailed.class));
                case "SubscriptionActivated" -> orchestrator.onSubscriptionActivated(objectMapper.readValue(body, SubscriptionActivated.class));
                case "SubscriptionActivationFailed" -> orchestrator.onSubscriptionActivationFailed(objectMapper.readValue(body, SubscriptionActivationFailed.class));
                case "MsisdnReleased" -> orchestrator.onMsisdnReleased(objectMapper.readValue(body, MsisdnReleased.class));
                case "PaymentRefunded" -> orchestrator.onPaymentRefunded(objectMapper.readValue(body, PaymentRefunded.class));
                default -> log.warn("Bilinmeyen saga reply tipi: {}", type);
            }
        };
    }

    /** eventType header'ini guvenli oku (Kafka binder String ya da byte[] verebilir). */
    private static String eventType(Message<byte[]> message) {
        Object raw = message.getHeaders().get(SagaHeaders.EVENT_TYPE);
        if (raw instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(raw);
    }
}
