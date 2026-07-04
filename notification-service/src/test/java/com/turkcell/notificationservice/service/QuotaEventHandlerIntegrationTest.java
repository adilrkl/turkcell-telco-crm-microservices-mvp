package com.turkcell.notificationservice.service;

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

import com.turkcell.commonlib.saga.QuotaThresholdReached;
import com.turkcell.notificationservice.repository.NotificationRepository;
import com.turkcell.notificationservice.repository.ProcessedEventRepository;

/**
 * Inbox idempotency entegrasyon testi (kota esik SMS'i) — GERCEK Postgres (Testcontainers)
 * + Flyway V1..V4 (V4, QUOTA_WARNING_80/QUOTA_EXCEEDED sablonlarini seed'ler; FK bunu ister).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(QuotaEventHandler.class)
@Testcontainers(disabledWithoutDocker = true)
class QuotaEventHandlerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    QuotaEventHandler handler;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    ProcessedEventRepository processedEventRepository;

    @Test
    @DisplayName("ayni QuotaThresholdReached eventId iki kez islenirse tek SMS yazilir")
    void sameEventTwiceCreatesSingleNotification() {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        QuotaThresholdReached event = threshold(eventId, customerId, 80);

        handler.handleThreshold(event);
        handler.handleThreshold(event); // redelivery simulasyonu (at-least-once)

        assertThat(notificationRepository.findAll())
                .as("ayni eventId ikinci kez islenmemeli")
                .hasSize(1)
                .first()
                .satisfies(n -> {
                    assertThat(n.getUserId()).isEqualTo(customerId);
                    assertThat(n.getTemplateCode()).isEqualTo("QUOTA_WARNING_80");
                    assertThat(n.getChannel()).isEqualTo("SMS");
                    assertThat(n.getStatus()).isEqualTo("SENT");
                });
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
    }

    @Test
    @DisplayName("%100 esigi QUOTA_EXCEEDED sablonunu kullanir")
    void hundredPercentUsesExceededTemplate() {
        handler.handleThreshold(threshold(UUID.randomUUID(), UUID.randomUUID(), 100));

        assertThat(notificationRepository.findAll())
                .hasSize(1)
                .first()
                .satisfies(n -> assertThat(n.getTemplateCode()).isEqualTo("QUOTA_EXCEEDED"));
    }

    private static QuotaThresholdReached threshold(UUID eventId, UUID customerId, int pct) {
        return new QuotaThresholdReached(eventId, UUID.randomUUID(), customerId,
                "905550001122", "VOICE",
                pct, pct >= 100 ? BigDecimal.ZERO : new BigDecimal("300"), new BigDecimal("1500"),
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1));
    }
}
