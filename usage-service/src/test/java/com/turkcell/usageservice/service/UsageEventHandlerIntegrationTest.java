package com.turkcell.usageservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
import org.springframework.data.domain.Pageable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.turkcell.usageservice.application.features.usage.mapper.UsageRecordMapper;
import com.turkcell.usageservice.entity.UsageRecord;
import com.turkcell.usageservice.event.UsageRecordedEvent;
import com.turkcell.usageservice.repository.ProcessedEventRepository;
import com.turkcell.usageservice.repository.UsageRecordRepository;
import com.turkcell.usageservice.saga.OutboxWriter;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Inbox idempotency entegrasyon testi — GERCEK Postgres (Testcontainers) + Flyway V1..V5.
 * DIKKAT: V3 migration'i demo abonelik (33333333-...) icin 4 usage_records satiri seed'ler;
 * bu yuzden assert'ler findAll() yerine testin rastgele subscriptionId'sine gore filtrelenir.
 * (Rastgele aboneliklerin hak fotografi yoktur -> kota dusumu devreye girmez; kota
 * senaryolari QuotaServiceIntegrationTest'tedir.)
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UsageEventHandler.class, UsageRecordMapper.class, QuotaService.class, OutboxWriter.class})
@Testcontainers(disabledWithoutDocker = true)
class UsageEventHandlerIntegrationTest {

    @TestConfiguration
    static class Config {
        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    UsageEventHandler handler;

    @Autowired
    UsageRecordRepository usageRecordRepository;

    @Autowired
    ProcessedEventRepository processedEventRepository;

    @Test
    @DisplayName("ayni eventId iki kez islenirse tek usage_records satiri yazilir")
    void sameEventTwiceCreatesSingleUsageRecord() {
        UUID eventId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        Instant recordedAt = Instant.parse("2026-07-01T10:15:30Z");
        UsageRecordedEvent event = new UsageRecordedEvent(eventId, subscriptionId,
                "DATA", new BigDecimal("512.0000"), recordedAt, "CDR-TEST-0001");

        handler.handle(event);
        handler.handle(event); // redelivery simulasyonu (at-least-once)

        List<UsageRecord> records = recordsOf(subscriptionId);
        assertThat(records)
                .as("ayni eventId ikinci kez islenmemeli")
                .hasSize(1)
                .first()
                .satisfies(r -> {
                    assertThat(r.getType()).isEqualTo("DATA");
                    assertThat(r.getQuantity()).isEqualByComparingTo("512.0000");
                    assertThat(r.getRecordedAt()).isEqualTo(recordedAt);
                    assertThat(r.getCdrRef()).isEqualTo("CDR-TEST-0001");
                });
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
    }

    @Test
    @DisplayName("farkli eventId'ler ayni abonelik icin farkli satirlar uretir")
    void distinctEventsCreateDistinctRecords() {
        UUID subscriptionId = UUID.randomUUID();

        handler.handle(new UsageRecordedEvent(UUID.randomUUID(), subscriptionId,
                "VOICE", new BigDecimal("12.5000"), Instant.now(), "CDR-TEST-0002"));
        handler.handle(new UsageRecordedEvent(UUID.randomUUID(), subscriptionId,
                "SMS", new BigDecimal("1.0000"), Instant.now(), "CDR-TEST-0003"));

        assertThat(recordsOf(subscriptionId)).hasSize(2);
    }

    @Test
    @DisplayName("recordedAt bos gelirse kayit ani (now) kullanilir")
    void missingRecordedAtDefaultsToNow() {
        UUID subscriptionId = UUID.randomUUID();
        Instant before = Instant.now();

        handler.handle(new UsageRecordedEvent(UUID.randomUUID(), subscriptionId,
                "DATA", new BigDecimal("42.0000"), null, "CDR-TEST-0004"));

        assertThat(recordsOf(subscriptionId))
                .hasSize(1)
                .first()
                .satisfies(r -> assertThat(r.getRecordedAt()).isAfterOrEqualTo(before));
    }

    private List<UsageRecord> recordsOf(UUID subscriptionId) {
        return usageRecordRepository.findBySubscriptionId(subscriptionId, Pageable.unpaged()).getContent();
    }
}
