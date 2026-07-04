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

import com.turkcell.commonlib.saga.CustomerKYCApproved;
import com.turkcell.notificationservice.repository.NotificationRepository;
import com.turkcell.notificationservice.repository.ProcessedEventRepository;

/**
 * Inbox idempotency entegrasyon testi (KYC onay SMS'i) — GERCEK Postgres (Testcontainers)
 * + Flyway V1..V6 (V6, KYC_APPROVED sablonunu seed'ler; FK bunu ister).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CustomerEventHandler.class, NotificationDispatcher.class, NotificationPreferenceService.class})
@Testcontainers(disabledWithoutDocker = true)
class CustomerEventHandlerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    CustomerEventHandler handler;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    ProcessedEventRepository processedEventRepository;

    @Test
    @DisplayName("ayni CustomerKYCApproved eventId iki kez islenirse tek SMS yazilir")
    void sameEventTwiceCreatesSingleNotification() {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        CustomerKYCApproved event = new CustomerKYCApproved(eventId, customerId, "Ahmet", "Yilmaz");

        handler.handleKycApproved(event);
        handler.handleKycApproved(event); // redelivery simulasyonu (at-least-once)

        assertThat(notificationRepository.findAll())
                .as("ayni eventId ikinci kez islenmemeli")
                .hasSize(1)
                .first()
                .satisfies(n -> {
                    assertThat(n.getUserId()).isEqualTo(customerId);
                    assertThat(n.getTemplateCode()).isEqualTo("KYC_APPROVED");
                    assertThat(n.getChannel()).isEqualTo("SMS");
                    assertThat(n.getStatus()).isEqualTo("SENT");
                });
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
    }

    @Test
    @DisplayName("farkli eventId'ler farkli bildirimler uretir")
    void distinctEventsCreateDistinctNotifications() {
        handler.handleKycApproved(new CustomerKYCApproved(UUID.randomUUID(), UUID.randomUUID(), "A", "B"));
        handler.handleKycApproved(new CustomerKYCApproved(UUID.randomUUID(), UUID.randomUUID(), "C", "D"));

        assertThat(notificationRepository.count()).isEqualTo(2);
    }
}
