package com.turkcell.paymentservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.turkcell.commonlib.saga.ChargePaymentCommand;
import com.turkcell.commonlib.saga.RefundPaymentCommand;
import com.turkcell.commonlib.saga.SagaTopics;
import com.turkcell.paymentservice.entity.Payment;
import com.turkcell.paymentservice.repository.OutboxRepository;
import com.turkcell.paymentservice.repository.PaymentAttemptRepository;
import com.turkcell.paymentservice.repository.PaymentRepository;
import com.turkcell.paymentservice.repository.ProcessedEventRepository;
import com.turkcell.paymentservice.saga.OutboxWriter;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Saga participant inbox idempotency entegrasyon testi — GERCEK Postgres (Testcontainers).
 * billing-service'teki kalibin payment'a yayilimi: is tablolari (payments/payment_attempts)
 * VE reply outbox'i (outbox_events) birlikte assert edilir; ayni eventId tekrar islenmez.
 * OutboxWriter'in ihtiyac duydugu Jackson 3 ObjectMapper'i slice saglamadigi icin test config verir.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PaymentSagaService.class, OutboxWriter.class, PaymentGateway.class, PaymentAuditWriter.class,
        InvoiceChargeProcessor.class, DunningService.class})
@Testcontainers(disabledWithoutDocker = true)
class PaymentSagaServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration(proxyBeanMethods = false)
    static class JacksonTestConfig {
        @Bean
        ObjectMapper testObjectMapper() {
            return JsonMapper.builder().build();
        }

    }

    @Autowired
    PaymentSagaService service;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    OutboxRepository outboxRepository;

    @Autowired
    ProcessedEventRepository processedEventRepository;

    @Test
    @DisplayName("ayni ChargePaymentCommand eventId iki kez islenirse tek payment + tek PaymentCompleted reply olusur")
    void sameChargeCommandTwiceCreatesSinglePaymentAndReply() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ChargePaymentCommand cmd = new ChargePaymentCommand(eventId, orderId, UUID.randomUUID(),
                new BigDecimal("249.90"), "TRY");

        service.charge(cmd);
        service.charge(cmd); // redelivery simulasyonu (at-least-once)

        assertThat(paymentRepository.findByOrderId(orderId))
                .as("ayni eventId ikinci kez islenmemeli")
                .get()
                .satisfies(p -> {
                    assertThat(p.getStatus()).isEqualTo("PAID");
                    assertThat(p.getAmount()).isEqualByComparingTo("249.90");
                    assertThat(p.getMethod()).isEqualTo("CARD");
                    assertThat(p.getPaidAt()).isNotNull();
                });
        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(paymentAttemptRepository.countByPaymentId(payment.getId())).isEqualTo(1);
        assertThat(countOutbox("PaymentCompleted", orderId)).isEqualTo(1);
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
    }

    @Test
    @DisplayName("1000 TRY ustu tahsilat reddedilir: payment FAILED + PaymentFailed reply outbox'a yazilir")
    void chargeOverThresholdFailsAndEnqueuesPaymentFailedReply() {
        UUID orderId = UUID.randomUUID();

        service.charge(new ChargePaymentCommand(UUID.randomUUID(), orderId, UUID.randomUUID(),
                new BigDecimal("1500.00"), "TRY"));

        assertThat(paymentRepository.findByOrderId(orderId))
                .get()
                .satisfies(p -> {
                    assertThat(p.getStatus()).isEqualTo("FAILED");
                    assertThat(p.getPaidAt()).isNull();
                });
        assertThat(countOutbox("PaymentFailed", orderId)).isEqualTo(1);
        assertThat(countOutbox("PaymentCompleted", orderId)).isZero();
    }

    @Test
    @DisplayName("refund mevcut odemeyi REFUNDED yapar ve PaymentRefunded reply outbox'a yazilir")
    void refundMarksPaymentRefundedAndEnqueuesReply() {
        UUID orderId = UUID.randomUUID();
        service.charge(new ChargePaymentCommand(UUID.randomUUID(), orderId, UUID.randomUUID(),
                new BigDecimal("249.90"), "TRY"));

        service.refund(new RefundPaymentCommand(UUID.randomUUID(), orderId, "activation failed"));

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo("REFUNDED");
        assertThat(paymentAttemptRepository.countByPaymentId(payment.getId()))
                .as("charge + refund iki attempt satiri yazmali")
                .isEqualTo(2);
        assertThat(countOutbox("PaymentRefunded", orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("ayni RefundPaymentCommand eventId iki kez islenirse tek PaymentRefunded reply olusur (payment satiri olmasa da)")
    void sameRefundCommandTwiceEnqueuesSingleReply() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID(); // hic charge edilmemis siparis: fire-and-forget ack yine doner
        RefundPaymentCommand cmd = new RefundPaymentCommand(eventId, orderId, "saga timeout");

        service.refund(cmd);
        service.refund(cmd);

        assertThat(paymentRepository.findByOrderId(orderId)).isEmpty();
        assertThat(countOutbox("PaymentRefunded", orderId)).isEqualTo(1);
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
    }

    private long countOutbox(String eventType, UUID orderId) {
        return outboxRepository.findAll().stream()
                .filter(e -> eventType.equals(e.getEventType()) && orderId.equals(e.getAggregateId()))
                .peek(e -> assertThat(e.getDestination()).isEqualTo(SagaTopics.SAGA_REPLIES))
                .count();
    }
}
