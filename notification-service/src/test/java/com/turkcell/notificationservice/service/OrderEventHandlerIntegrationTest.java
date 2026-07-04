package com.turkcell.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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

import com.turkcell.commonlib.saga.OrderCancelled;
import com.turkcell.commonlib.saga.OrderConfirmed;
import com.turkcell.notificationservice.repository.NotificationRepository;
import com.turkcell.notificationservice.repository.ProcessedEventRepository;

/**
 * Inbox idempotency entegrasyon testi — GERCEK Postgres (Testcontainers) + Flyway V1..V3.
 * billing-service'teki OrderEventHandlerIntegrationTest kalibinin notification'a yayilimi:
 * ayni eventId iki kez islenirse tek notification satiri yazilmali (processed_events kilidi).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OrderEventHandler.class)
@Testcontainers(disabledWithoutDocker = true)
class OrderEventHandlerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    OrderEventHandler handler;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    ProcessedEventRepository processedEventRepository;

    @Test
    @DisplayName("ayni OrderConfirmed eventId iki kez islenirse tek notification yazilir")
    void sameConfirmedEventTwiceCreatesSingleNotification() {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderConfirmed event = new OrderConfirmed(eventId, UUID.randomUUID(), customerId,
                UUID.randomUUID(), "TARIFE_M", "905550001122", new BigDecimal("249.90"), "TRY",
                1500, 1000, 15360);

        handler.handleConfirmed(event);
        handler.handleConfirmed(event); // redelivery simulasyonu (at-least-once)

        assertThat(notificationRepository.findAll())
                .as("ayni eventId ikinci kez islenmemeli")
                .hasSize(1)
                .first()
                .satisfies(n -> {
                    assertThat(n.getUserId()).isEqualTo(customerId);
                    assertThat(n.getTemplateCode()).isEqualTo("ORDER_CONFIRMED");
                    assertThat(n.getChannel()).isEqualTo("SMS");
                    assertThat(n.getStatus()).isEqualTo("SENT");
                });
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
    }

    @Test
    @DisplayName("ayni OrderCancelled eventId iki kez islenirse tek iptal bildirimi yazilir")
    void sameCancelledEventTwiceCreatesSingleNotification() {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancelled event = new OrderCancelled(eventId, UUID.randomUUID(), customerId,
                "Odeme basarisiz: tutar limiti asti");

        handler.handleCancelled(event);
        handler.handleCancelled(event);

        assertThat(notificationRepository.findAll())
                .hasSize(1)
                .first()
                .satisfies(n -> {
                    assertThat(n.getUserId()).isEqualTo(customerId);
                    assertThat(n.getTemplateCode()).isEqualTo("ORDER_CANCELLED");
                    assertThat(n.getChannel()).isEqualTo("SMS");
                    assertThat(n.getStatus()).isEqualTo("SENT");
                });
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
    }

    @Test
    @DisplayName("farkli eventId'ler farkli notification satirlari uretir")
    void distinctEventsCreateDistinctNotifications() {
        handler.handleConfirmed(confirmed());
        handler.handleCancelled(new OrderCancelled(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "MSISDN rezerve edilemedi"));

        assertThat(notificationRepository.count()).isEqualTo(2);
    }

    private static OrderConfirmed confirmed() {
        return new OrderConfirmed(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "TARIFE_M", "905550009999", new BigDecimal("100.00"), "TRY",
                1500, 1000, 15360);
    }
}
