package com.turkcell.paymentservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

import com.turkcell.commonlib.saga.ChargeInvoiceCommand;
import com.turkcell.commonlib.saga.SagaTopics;
import com.turkcell.paymentservice.entity.DunningSchedule;
import com.turkcell.paymentservice.entity.DunningStatus;
import com.turkcell.paymentservice.repository.DunningScheduleRepository;
import com.turkcell.paymentservice.repository.OutboxRepository;
import com.turkcell.paymentservice.repository.PaymentRepository;
import com.turkcell.paymentservice.saga.OutboxWriter;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Dunning retry (G8, FR-27) — GERCEK Postgres (Testcontainers) + Flyway V1..V4 (V4 dunning tablosu).
 * Dogrular: (1) basarisiz fatura tahsilati dunning plani acar; (2) vadesi gelen plan basarili
 * retry'de RESOLVED + InvoicePaid; (3) esik ustu tutar tum retry'lardan sonra EXHAUSTED.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PaymentSagaService.class, DunningService.class, InvoiceChargeProcessor.class,
        PaymentGateway.class, PaymentAuditWriter.class, OutboxWriter.class})
@Testcontainers(disabledWithoutDocker = true)
class DunningServiceIntegrationTest {

    private static final List<Duration> INTERVALS =
            List.of(Duration.ofHours(24), Duration.ofHours(72), Duration.ofHours(168));

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** DunningProperties'i app'in @EnableConfigurationProperties'i saglar (varsayilan 24/72/168h). */
    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean
        ObjectMapper testObjectMapper() {
            return JsonMapper.builder().build();
        }
    }

    @Autowired
    PaymentSagaService paymentSagaService;

    @Autowired
    DunningService dunningService;

    @Autowired
    DunningScheduleRepository scheduleRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    OutboxRepository outboxRepository;

    @Test
    @DisplayName("esik ustu fatura tahsilati basarisiz: InvoicePaymentFailed + ilk retry origin+24s planlanir")
    void failedInvoiceChargeOpensDunningSchedule() {
        UUID invoiceId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant before = Instant.now();

        paymentSagaService.chargeInvoice(new ChargeInvoiceCommand(
                UUID.randomUUID(), invoiceId, customerId, new BigDecimal("1500.00"), "TRY"));

        assertThat(countOutbox("InvoicePaymentFailed", invoiceId)).isEqualTo(1);
        assertThat(countOutbox("InvoicePaid", invoiceId)).isZero();

        DunningSchedule schedule = scheduleRepository.findByInvoiceId(invoiceId).orElseThrow();
        assertThat(schedule.getStatus()).isEqualTo(DunningStatus.PENDING);
        assertThat(schedule.getRetryCount()).isZero();
        assertThat(schedule.getMaxRetries()).isEqualTo(3);
        // ilk retry origin + 24s (calisma anina gore tolerans)
        assertThat(schedule.getNextRetryAt())
                .isBetween(before.plus(INTERVALS.get(0)).minusSeconds(30),
                        Instant.now().plus(INTERVALS.get(0)).plusSeconds(30));
    }

    @Test
    @DisplayName("vadesi gelen plan basarili retry'de RESOLVED + InvoicePaid yayinlanir, PAID payment yazilir")
    void dueScheduleResolvesOnSuccessfulRetry() {
        UUID invoiceId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        // Odenebilir tutar (esik alti) -> retry ONAYLANIR.
        scheduleRepository.save(pendingSchedule(invoiceId, customerId, new BigDecimal("249.90"), 0,
                Instant.now().minusSeconds(60)));

        dunningService.retryDue();

        DunningSchedule schedule = scheduleRepository.findByInvoiceId(invoiceId).orElseThrow();
        assertThat(schedule.getStatus()).isEqualTo(DunningStatus.RESOLVED);
        assertThat(countOutbox("InvoicePaid", invoiceId)).isEqualTo(1);
        assertThat(paymentRepository.findAll())
                .filteredOn(p -> invoiceId.equals(p.getInvoiceId()))
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.getStatus()).isEqualTo("PAID");
                    assertThat(p.getMethod()).isEqualTo("AUTO_PAY_RETRY");
                });
    }

    @Test
    @DisplayName("son retry de basarisizsa plan EXHAUSTED olur, InvoicePaid yayinlanmaz")
    void lastFailedRetryExhaustsSchedule() {
        UUID invoiceId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        // Esik ustu tutar + son denemeye gelmis plan (retryCount = maxRetries-1).
        scheduleRepository.save(pendingSchedule(invoiceId, customerId, new BigDecimal("1500.00"), 2,
                Instant.now().minusSeconds(60)));

        dunningService.retryDue();

        DunningSchedule schedule = scheduleRepository.findByInvoiceId(invoiceId).orElseThrow();
        assertThat(schedule.getStatus()).isEqualTo(DunningStatus.EXHAUSTED);
        assertThat(schedule.getRetryCount()).isEqualTo(3);
        assertThat(countOutbox("InvoicePaid", invoiceId)).isZero();
    }

    private DunningSchedule pendingSchedule(UUID invoiceId, UUID customerId, BigDecimal amount,
                                            int retryCount, Instant nextRetryAt) {
        Instant origin = Instant.now().minus(INTERVALS.get(0));
        DunningSchedule s = new DunningSchedule();
        s.setInvoiceId(invoiceId);
        s.setCustomerId(customerId);
        s.setAmount(amount);
        s.setCurrency("TRY");
        s.setRetryCount(retryCount);
        s.setMaxRetries(INTERVALS.size());
        s.setOriginFailedAt(origin);
        s.setNextRetryAt(nextRetryAt);
        s.setStatus(DunningStatus.PENDING);
        s.setCreatedAt(Instant.now());
        return s;
    }

    private long countOutbox(String eventType, UUID invoiceId) {
        return outboxRepository.findAll().stream()
                .filter(e -> eventType.equals(e.getEventType()) && invoiceId.equals(e.getAggregateId()))
                .peek(e -> assertThat(e.getDestination()).isEqualTo(SagaTopics.INVOICE_EVENTS))
                .count();
    }
}
