package com.turkcell.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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

import com.turkcell.commonlib.saga.TicketOpened;
import com.turkcell.notificationservice.repository.NotificationRepository;
import com.turkcell.notificationservice.repository.ProcessedEventRepository;

/**
 * Inbox idempotency entegrasyon testi (destek talebi acilis SMS'i, G7) —
 * GERCEK Postgres (Testcontainers) + Flyway V1..V8 (V8 TICKET_OPENED sablonunu seed'ler; FK bunu ister).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TicketEventHandler.class)
@Testcontainers(disabledWithoutDocker = true)
class TicketEventHandlerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    TicketEventHandler handler;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    ProcessedEventRepository processedEventRepository;

    @Test
    @DisplayName("ayni TicketOpened eventId iki kez islenirse tek TICKET_OPENED SMS'i yazilir")
    void sameOpenedEventTwiceCreatesSingleNotification() {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        TicketOpened event = new TicketOpened(eventId, UUID.randomUUID(), customerId,
                "BILLING", "HIGH", "BILLING_TEAM", Instant.now().plusSeconds(28800));

        handler.handleOpened(event);
        handler.handleOpened(event); // redelivery simulasyonu (at-least-once)

        assertThat(notificationRepository.findAll())
                .as("ayni eventId ikinci kez islenmemeli")
                .hasSize(1)
                .first()
                .satisfies(n -> {
                    assertThat(n.getUserId()).isEqualTo(customerId);
                    assertThat(n.getTemplateCode()).isEqualTo("TICKET_OPENED");
                    assertThat(n.getChannel()).isEqualTo("SMS");
                    assertThat(n.getStatus()).isEqualTo("SENT");
                });
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
    }
}
