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

import com.turkcell.commonlib.saga.InvoiceGenerated;
import com.turkcell.commonlib.saga.InvoicePaid;
import com.turkcell.commonlib.saga.InvoicePaymentFailed;
import com.turkcell.notificationservice.repository.NotificationRepository;
import com.turkcell.notificationservice.repository.ProcessedEventRepository;

/**
 * Inbox idempotency entegrasyon testi (fatura bildirimleri) — GERCEK Postgres (Testcontainers)
 * + Flyway V1..V5 (V5, INVOICE_* sablonlarini seed'ler; FK bunu ister).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({InvoiceEventHandler.class, NotificationDispatcher.class, NotificationPreferenceService.class})
@Testcontainers(disabledWithoutDocker = true)
class InvoiceEventHandlerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    InvoiceEventHandler handler;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    ProcessedEventRepository processedEventRepository;

    @Test
    @DisplayName("ayni InvoiceGenerated eventId iki kez islenirse tek EMAIL yazilir")
    void sameGeneratedEventTwiceCreatesSingleNotification() {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        InvoiceGenerated event = new InvoiceGenerated(eventId, UUID.randomUUID(), customerId,
                UUID.randomUUID(), new BigDecimal("299.88"), "TRY",
                LocalDate.of(2026, 7, 18), LocalDate.of(2026, 6, 3), LocalDate.of(2026, 7, 3));

        handler.handleGenerated(event);
        handler.handleGenerated(event); // redelivery simulasyonu (at-least-once)

        assertThat(notificationRepository.findAll())
                .as("ayni eventId ikinci kez islenmemeli")
                .hasSize(1)
                .first()
                .satisfies(n -> {
                    assertThat(n.getUserId()).isEqualTo(customerId);
                    assertThat(n.getTemplateCode()).isEqualTo("INVOICE_GENERATED");
                    assertThat(n.getChannel()).isEqualTo("EMAIL");
                    assertThat(n.getStatus()).isEqualTo("SENT");
                });
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
    }

    @Test
    @DisplayName("InvoicePaid odeme bildirimi uretir (idempotent)")
    void paidEventCreatesSingleNotification() {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        InvoicePaid event = new InvoicePaid(eventId, UUID.randomUUID(), UUID.randomUUID(),
                customerId, new BigDecimal("299.88"), "TRY");

        handler.handlePaid(event);
        handler.handlePaid(event);

        assertThat(notificationRepository.findAll())
                .hasSize(1)
                .first()
                .satisfies(n -> {
                    assertThat(n.getUserId()).isEqualTo(customerId);
                    assertThat(n.getTemplateCode()).isEqualTo("INVOICE_PAID");
                    assertThat(n.getChannel()).isEqualTo("EMAIL");
                });
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
    }

    @Test
    @DisplayName("InvoicePaymentFailed basarisiz odeme bildirimi uretir")
    void paymentFailedEventCreatesNotification() {
        UUID customerId = UUID.randomUUID();
        handler.handlePaymentFailed(new InvoicePaymentFailed(UUID.randomUUID(), UUID.randomUUID(),
                "tutar limiti asti: 1500.00", customerId, new BigDecimal("1500.00"), "TRY"));

        assertThat(notificationRepository.findAll())
                .hasSize(1)
                .first()
                .satisfies(n -> {
                    assertThat(n.getUserId()).isEqualTo(customerId);
                    assertThat(n.getTemplateCode()).isEqualTo("INVOICE_PAYMENT_FAILED");
                    assertThat(n.getChannel()).isEqualTo("EMAIL");
                });
    }
}
