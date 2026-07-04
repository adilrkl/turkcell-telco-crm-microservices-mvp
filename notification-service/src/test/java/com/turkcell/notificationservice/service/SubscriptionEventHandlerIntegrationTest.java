package com.turkcell.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;

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

import com.turkcell.commonlib.saga.SubscriptionReactivated;
import com.turkcell.commonlib.saga.SubscriptionSuspended;
import com.turkcell.commonlib.saga.SubscriptionTerminated;
import com.turkcell.notificationservice.repository.NotificationRepository;
import com.turkcell.notificationservice.repository.ProcessedEventRepository;

/**
 * Inbox idempotency entegrasyon testi (abonelik yasam dongusu SMS'leri, G4) —
 * GERCEK Postgres (Testcontainers) + Flyway V1..V7 (V7 sablonlari seed'ler; FK bunu ister).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SubscriptionEventHandler.class, NotificationDispatcher.class, NotificationPreferenceService.class})
@Testcontainers(disabledWithoutDocker = true)
class SubscriptionEventHandlerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    SubscriptionEventHandler handler;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    ProcessedEventRepository processedEventRepository;

    @Test
    @DisplayName("ayni SubscriptionSuspended eventId iki kez islenirse tek SMS yazilir")
    void sameSuspendedEventTwiceCreatesSingleNotification() {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        SubscriptionSuspended event = new SubscriptionSuspended(
                eventId, UUID.randomUUID(), customerId, "905320000001", "odeme gecikmesi");

        handler.handleSuspended(event);
        handler.handleSuspended(event); // redelivery simulasyonu (at-least-once)

        assertThat(notificationRepository.findAll())
                .as("ayni eventId ikinci kez islenmemeli")
                .hasSize(1)
                .first()
                .satisfies(n -> {
                    assertThat(n.getUserId()).isEqualTo(customerId);
                    assertThat(n.getTemplateCode()).isEqualTo("SUBSCRIPTION_SUSPENDED");
                    assertThat(n.getChannel()).isEqualTo("SMS");
                    assertThat(n.getStatus()).isEqualTo("SENT");
                });
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
    }

    @Test
    @DisplayName("reactivate ve terminate event'leri kendi sablon kodlariyla SMS uretir")
    void reactivatedAndTerminatedUseOwnTemplates() {
        handler.handleReactivated(new SubscriptionReactivated(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "905320000002"));
        handler.handleTerminated(new SubscriptionTerminated(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "905320000003", "musteri istegi"));

        assertThat(notificationRepository.findAll())
                .hasSize(2)
                .extracting("templateCode")
                .containsExactlyInAnyOrder("SUBSCRIPTION_REACTIVATED", "SUBSCRIPTION_TERMINATED");
    }
}
