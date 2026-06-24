package com.turkcell.paymentservice.consumer;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import com.turkcell.commonlib.saga.ChargePaymentCommand;
import com.turkcell.commonlib.saga.RefundPaymentCommand;
import com.turkcell.commonlib.saga.SagaHeaders;
import com.turkcell.paymentservice.service.PaymentSagaService;

import tools.jackson.databind.ObjectMapper;

/**
 * payment-commands topic tuketicisi (saga participant).
 * ChargePayment / RefundPayment komutlarini eventType header'ina gore dispatch eder.
 */
@Configuration
public class PaymentCommandConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentCommandConsumer.class);

    private final PaymentSagaService service;
    private final ObjectMapper objectMapper;

    public PaymentCommandConsumer(PaymentSagaService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @Bean
    public Consumer<Message<byte[]>> paymentCommands() {
        return message -> {
            String type = eventType(message);
            byte[] body = message.getPayload();
            switch (type) {
                case "ChargePaymentCommand" -> service.charge(objectMapper.readValue(body, ChargePaymentCommand.class));
                case "RefundPaymentCommand" -> service.refund(objectMapper.readValue(body, RefundPaymentCommand.class));
                default -> log.warn("Bilinmeyen payment komut tipi: {}", type);
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
