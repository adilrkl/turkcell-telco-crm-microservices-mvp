package com.turkcell.billingservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.turkcell.billingservice.repository.PendingChargeRepository;
import com.turkcell.billingservice.repository.ProcessedEventRepository;
import com.turkcell.commonlib.saga.OverageRecorded;

/**
 * Inbox idempotency entegrasyon testi (asim ucreti) — GERCEK Postgres (Testcontainers)
 * + Flyway V1..V4. Birim fiyat default'lari kullanilir (VOICE 0.50/dk).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OverageEventHandler.class)
@Testcontainers(disabledWithoutDocker = true)
class OverageEventHandlerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    OverageEventHandler handler;

    @Autowired
    PendingChargeRepository pendingChargeRepository;

    @Autowired
    ProcessedEventRepository processedEventRepository;

    @Test
    @DisplayName("ayni OverageRecorded eventId iki kez islenirse tek pending_charges satiri yazilir")
    void sameEventTwiceCreatesSinglePendingCharge() {
        UUID eventId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        OverageRecorded event = new OverageRecorded(eventId, subscriptionId, UUID.randomUUID(),
                "VOICE", new BigDecimal("12.5000"), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1));

        handler.handle(event);
        handler.handle(event); // redelivery simulasyonu (at-least-once)

        assertThat(pendingChargeRepository.findAll())
                .as("ayni eventId ikinci kez islenmemeli")
                .hasSize(1)
                .first()
                .satisfies(c -> {
                    assertThat(c.getSubscriptionId()).isEqualTo(subscriptionId);
                    assertThat(c.getType()).isEqualTo("VOICE");
                    assertThat(c.getQuantity()).isEqualByComparingTo("12.5");
                    assertThat(c.getUnitPrice()).isEqualByComparingTo("0.50");
                    assertThat(c.getAmount()).isEqualByComparingTo("6.25"); // 12.5 dk x 0.50
                    assertThat(c.getStatus()).isEqualTo("PENDING");
                    assertThat(c.getInvoiceId()).isNull();
                });
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
    }

    @Test
    @DisplayName("farkli eventId'ler ayri asim satirlari uretir")
    void distinctEventsCreateDistinctCharges() {
        UUID subscriptionId = UUID.randomUUID();
        handler.handle(new OverageRecorded(UUID.randomUUID(), subscriptionId, UUID.randomUUID(),
                "SMS", BigDecimal.ONE, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1)));
        handler.handle(new OverageRecorded(UUID.randomUUID(), subscriptionId, UUID.randomUUID(),
                "DATA", new BigDecimal("256"), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1)));

        assertThat(pendingChargeRepository.count()).isEqualTo(2);
    }
}
