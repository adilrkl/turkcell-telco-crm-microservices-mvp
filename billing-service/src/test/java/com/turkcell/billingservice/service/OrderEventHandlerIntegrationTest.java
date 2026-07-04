package com.turkcell.billingservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.turkcell.billingservice.repository.BillCycleRepository;
import com.turkcell.billingservice.repository.ProcessedEventRepository;
import com.turkcell.commonlib.saga.OrderConfirmed;

/**
 * Inbox idempotency entegrasyon testi — GERCEK Postgres (Testcontainers) + Flyway V1..V3.
 * Ayni eventId iki kez islenirse yalnizca BIR bill_cycle acilmali (processed_events kilidi).
 * Platform genelindeki outbox/inbox deseninin ilk kalip (pattern-setter) testi.
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
    BillCycleRepository billCycleRepository;

    @Autowired
    ProcessedEventRepository processedEventRepository;

    @Test
    void sameEventTwiceCreatesSingleBillCycle() {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        OrderConfirmed event = new OrderConfirmed(eventId, UUID.randomUUID(), customerId,
                subscriptionId, "TARIFE_M", "905550001122", new BigDecimal("249.90"), "TRY",
                1500, 1000, 15360);

        handler.handle(event);
        handler.handle(event); // redelivery simulasyonu (at-least-once)

        assertThat(billCycleRepository.findAll())
                .as("ayni eventId ikinci kez islenmemeli")
                .hasSize(1)
                .first()
                .satisfies(cycle -> {
                    assertThat(cycle.getCustomerId()).isEqualTo(customerId);
                    assertThat(cycle.getSubscriptionId()).isEqualTo(subscriptionId);
                    assertThat(cycle.getMonthlyFee()).isEqualByComparingTo("249.90");
                    assertThat(cycle.getCurrency()).isEqualTo("TRY");
                });
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
    }

    @Test
    void distinctEventsCreateDistinctCycles() {
        OrderConfirmed first = confirmed();
        OrderConfirmed second = confirmed();

        handler.handle(first);
        handler.handle(second);

        assertThat(billCycleRepository.count()).isEqualTo(2);
    }

    private static OrderConfirmed confirmed() {
        return new OrderConfirmed(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "TARIFE_M", "905550009999", new BigDecimal("100.00"), "TRY",
                1500, 1000, 15360);
    }
}
