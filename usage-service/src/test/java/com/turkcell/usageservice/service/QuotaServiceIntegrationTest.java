package com.turkcell.usageservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

import com.turkcell.commonlib.saga.OrderConfirmed;
import com.turkcell.commonlib.saga.OverageRecorded;
import com.turkcell.commonlib.saga.QuotaThresholdReached;
import com.turkcell.usageservice.entity.OutboxEvent;
import com.turkcell.usageservice.entity.Quota;
import com.turkcell.usageservice.repository.OutboxRepository;
import com.turkcell.usageservice.repository.ProcessedEventRepository;
import com.turkcell.usageservice.repository.QuotaRepository;
import com.turkcell.usageservice.repository.SubscriptionEntitlementRepository;
import com.turkcell.usageservice.saga.OutboxWriter;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kota zinciri entegrasyon testi (G1) — GERCEK Postgres (Testcontainers) + Flyway V1..V5:
 * hak fotografi + kota acilisi (idempotent), dusum, %80/%100 esik event'leri (esik basina
 * tek), asim event'leri ve yeni donemde lazy kota acilisi. Event'ler outbox'tan dogrulanir.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QuotaService.class, OrderEventHandler.class, OutboxWriter.class})
@Testcontainers(disabledWithoutDocker = true)
class QuotaServiceIntegrationTest {

    @TestConfiguration
    static class Config {
        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }
    }

    static final ObjectMapper json = JsonMapper.builder().build();
    static final LocalDate JULY = LocalDate.of(2026, 7, 1);
    static final Instant IN_JULY = Instant.parse("2026-07-15T10:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    QuotaService quotaService;

    @Autowired
    OrderEventHandler orderEventHandler;

    @Autowired
    QuotaRepository quotaRepository;

    @Autowired
    SubscriptionEntitlementRepository entitlementRepository;

    @Autowired
    OutboxRepository outboxRepository;

    @Autowired
    ProcessedEventRepository processedEventRepository;

    @Test
    @DisplayName("ayni OrderConfirmed eventId iki kez islenirse tek hak fotografi + tek kota acilir")
    void provisionIsIdempotent() {
        UUID subscriptionId = UUID.randomUUID();
        OrderConfirmed event = confirmed(UUID.randomUUID(), subscriptionId, 100, 50, 1000);

        orderEventHandler.handleConfirmed(event);
        orderEventHandler.handleConfirmed(event); // redelivery simulasyonu (at-least-once)

        assertThat(entitlementRepository.findById(subscriptionId)).isPresent()
                .hasValueSatisfying(ent -> {
                    assertThat(ent.getTariffCode()).isEqualTo("TARIFE_T");
                    assertThat(ent.getMinutesIncluded()).isEqualTo(100);
                    assertThat(ent.getSmsIncluded()).isEqualTo(50);
                    assertThat(ent.getMbIncluded()).isEqualTo(1000);
                });
        assertThat(quotaRepository.findAll())
                .filteredOn(q -> q.getSubscriptionId().equals(subscriptionId))
                .hasSize(1)
                .first()
                .satisfies(q -> {
                    assertThat(q.getMinutesTotal()).isEqualByComparingTo("100");
                    assertThat(q.getMinutesRemaining()).isEqualByComparingTo("100");
                });
        assertThat(processedEventRepository.existsById(event.eventId())).isTrue();
    }

    @Test
    @DisplayName("kullanim kotadan duser; esik gecilmedikce event uretilmez")
    void usageDeductsWithoutEvents() {
        UUID subscriptionId = provisioned(100, 50, 1000);

        quotaService.applyUsage(subscriptionId, "VOICE", new BigDecimal("30"), IN_JULY);

        Quota quota = julyQuota(subscriptionId);
        assertThat(quota.getMinutesRemaining()).isEqualByComparingTo("70");
        assertThat(outboxOf(subscriptionId)).isEmpty();
    }

    @Test
    @DisplayName("%80 esigi gecilince TEK QuotaThresholdReached(80) yayinlanir (tekrar yok)")
    void eightyPercentPublishedOnce() {
        UUID subscriptionId = provisioned(100, 50, 1000);

        quotaService.applyUsage(subscriptionId, "VOICE", new BigDecimal("85"), IN_JULY);
        quotaService.applyUsage(subscriptionId, "VOICE", new BigDecimal("5"), IN_JULY); // hala %80 bandinda

        List<OutboxEvent> events = outboxOf(subscriptionId);
        assertThat(events).hasSize(1);
        QuotaThresholdReached payload = json.readValue(events.getFirst().getPayload(), QuotaThresholdReached.class);
        assertThat(payload.thresholdPct()).isEqualTo(80);
        assertThat(payload.type()).isEqualTo("VOICE");
        assertThat(payload.remaining()).isEqualByComparingTo("15"); // esik anindaki kalan (85 dk sonrasi)
        assertThat(payload.total()).isEqualByComparingTo("100");
        assertThat(payload.periodStart()).isEqualTo(JULY);
    }

    @Test
    @DisplayName("kota tukenince %100 event'i + asim OverageRecorded olarak akar")
    void exhaustionPublishesHundredAndOverage() {
        UUID subscriptionId = provisioned(100, 50, 1000);

        // 120 dk tek seferde: %80 ve %100 birden gecilir -> yalnizca %100 yayinlanir; 20 dk asim.
        quotaService.applyUsage(subscriptionId, "VOICE", new BigDecimal("120"), IN_JULY);

        Quota quota = julyQuota(subscriptionId);
        assertThat(quota.getMinutesRemaining()).isEqualByComparingTo("0");

        List<OutboxEvent> events = outboxOf(subscriptionId);
        assertThat(events).hasSize(2);
        QuotaThresholdReached threshold = json.readValue(
                eventOf(events, "QuotaThresholdReached").getPayload(), QuotaThresholdReached.class);
        assertThat(threshold.thresholdPct()).isEqualTo(100);
        OverageRecorded overage = json.readValue(
                eventOf(events, "OverageRecorded").getPayload(), OverageRecorded.class);
        assertThat(overage.quantity()).isEqualByComparingTo("20");
        assertThat(overage.type()).isEqualTo("VOICE");

        // Kota bittikten sonraki her kullanim yeni asim uretir, esik event'i tekrarlanmaz.
        quotaService.applyUsage(subscriptionId, "VOICE", new BigDecimal("7"), IN_JULY);
        List<OutboxEvent> after = outboxOf(subscriptionId);
        assertThat(after).hasSize(3);
        assertThat(after.stream().filter(e -> e.getEventType().equals("OverageRecorded"))).hasSize(2);
    }

    @Test
    @DisplayName("hak fotografi olmayan abonelikte kota takibi yapilmaz")
    void unknownSubscriptionSkipsQuota() {
        UUID subscriptionId = UUID.randomUUID();

        quotaService.applyUsage(subscriptionId, "DATA", new BigDecimal("512"), IN_JULY);

        assertThat(quotaRepository.findBySubscriptionIdAndPeriodStart(subscriptionId, JULY)).isEmpty();
        assertThat(outboxOf(subscriptionId)).isEmpty();
    }

    @Test
    @DisplayName("yeni donemde kota hak fotografindan lazy acilir (aylik yenileme)")
    void newPeriodOpensFreshQuota() {
        UUID subscriptionId = provisioned(100, 50, 1000);
        quotaService.applyUsage(subscriptionId, "VOICE", new BigDecimal("90"), IN_JULY);

        // Agustos kullanimi: temmuz kotasindan bagimsiz, tam haktan acilir.
        quotaService.applyUsage(subscriptionId, "VOICE", new BigDecimal("10"),
                Instant.parse("2026-08-02T09:00:00Z"));

        Quota august = quotaRepository
                .findBySubscriptionIdAndPeriodStart(subscriptionId, LocalDate.of(2026, 8, 1))
                .orElseThrow();
        assertThat(august.getMinutesRemaining()).isEqualByComparingTo("90");
        assertThat(august.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(julyQuota(subscriptionId).getMinutesRemaining()).isEqualByComparingTo("10");
    }

    // --- yardimcilar ---

    /** Hak fotografini dogrudan provision ile yazar (dk/sms/mb haklariyla). */
    private UUID provisioned(int minutes, int sms, int mb) {
        UUID subscriptionId = UUID.randomUUID();
        quotaService.provision(confirmed(UUID.randomUUID(), subscriptionId, minutes, sms, mb));
        // provision icinde bulunulan ay icin kota acar; testler IN_JULY donemine yazar,
        // o donemin kotasi ilk kullanim aninda lazy acilir (rollover ile ayni yol).
        return subscriptionId;
    }

    private static OrderConfirmed confirmed(UUID eventId, UUID subscriptionId,
                                            Integer minutes, Integer sms, Integer mb) {
        return new OrderConfirmed(eventId, UUID.randomUUID(), UUID.randomUUID(), subscriptionId,
                "TARIFE_T", "905550001122", new BigDecimal("99.90"), "TRY", minutes, sms, mb);
    }

    private Quota julyQuota(UUID subscriptionId) {
        return quotaRepository.findBySubscriptionIdAndPeriodStart(subscriptionId, JULY).orElseThrow();
    }

    private List<OutboxEvent> outboxOf(UUID subscriptionId) {
        return outboxRepository.findAll().stream()
                .filter(e -> subscriptionId.equals(e.getAggregateId()))
                .toList();
    }

    private static OutboxEvent eventOf(List<OutboxEvent> events, String eventType) {
        return events.stream().filter(e -> e.getEventType().equals(eventType)).findFirst().orElseThrow();
    }
}
