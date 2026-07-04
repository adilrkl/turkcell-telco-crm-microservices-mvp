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

import com.turkcell.notificationservice.repository.NotificationRepository;
import com.turkcell.notificationservice.repository.ProcessedEventRepository;

/**
 * Bildirim gonderim yolu (FR-30) — GERCEK Postgres (Testcontainers) + Flyway V1..V9.
 * Dogrular: varsayilan opt-in -> SENT; opt-out -> SKIPPED (gonderim yok) ama event yine
 * processed islaretlenir; opt-out bir kanali digerini etkilemez. Template "ORDER_CONFIRMED"
 * seed'li (notifications.template_code FK'si bunu ister).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({NotificationDispatcher.class, NotificationPreferenceService.class})
@Testcontainers(disabledWithoutDocker = true)
class NotificationDispatcherIntegrationTest {

    private static final String TPL = "ORDER_CONFIRMED";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    NotificationDispatcher dispatcher;

    @Autowired
    NotificationPreferenceService preferences;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    ProcessedEventRepository processedEventRepository;

    @Test
    @DisplayName("varsayilan (tercih yok) -> SENT, sentAt dolu")
    void defaultOptInSendsNotification() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        dispatcher.dispatch(eventId, userId, TPL, "SMS", "detay");

        assertThat(notificationRepository.findAll())
                .filteredOn(n -> userId.equals(n.getUserId()))
                .singleElement()
                .satisfies(n -> {
                    assertThat(n.getStatus()).isEqualTo("SENT");
                    assertThat(n.getChannel()).isEqualTo("SMS");
                    assertThat(n.getSentAt()).isNotNull();
                });
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
    }

    @Test
    @DisplayName("opt-out kanalda -> SKIPPED (sentAt yok) ama event processed islaretlenir")
    void optOutSkipsSendButMarksProcessed() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        preferences.setPreference(userId, "SMS", false);

        dispatcher.dispatch(eventId, userId, TPL, "SMS", "detay");

        assertThat(notificationRepository.findAll())
                .filteredOn(n -> userId.equals(n.getUserId()))
                .singleElement()
                .satisfies(n -> {
                    assertThat(n.getStatus()).isEqualTo("SKIPPED");
                    assertThat(n.getSentAt()).isNull();
                });
        assertThat(processedEventRepository.existsById(eventId))
                .as("opt-out olsa da redelivery tekrar denemesin diye processed islaretlenir")
                .isTrue();
    }

    @Test
    @DisplayName("bir kanaldan opt-out digerini etkilemez (SMS kapali, EMAIL SENT)")
    void optOutOnOneChannelDoesNotBlockAnother() {
        UUID userId = UUID.randomUUID();
        preferences.setPreference(userId, "SMS", false);

        dispatcher.dispatch(UUID.randomUUID(), userId, TPL, "EMAIL", "detay");

        assertThat(notificationRepository.findAll())
                .filteredOn(n -> userId.equals(n.getUserId()))
                .singleElement()
                .satisfies(n -> {
                    assertThat(n.getChannel()).isEqualTo("EMAIL");
                    assertThat(n.getStatus()).isEqualTo("SENT");
                });
    }
}
