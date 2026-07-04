package com.turkcell.ticketservice.application.features.ticket.command.create;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.turkcell.commonlib.saga.SagaTopics;
import com.turkcell.ticketservice.application.features.ticket.mapper.TicketMapper;
import com.turkcell.ticketservice.application.features.ticket.rule.TicketOpeningPolicy;
import com.turkcell.ticketservice.dto.TicketResponse;
import com.turkcell.ticketservice.entity.OutboxEvent;
import com.turkcell.ticketservice.entity.OutboxStatus;
import com.turkcell.ticketservice.repository.OutboxRepository;
import com.turkcell.ticketservice.repository.TicketRepository;
import com.turkcell.ticketservice.saga.OutboxWriter;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Talep acilis akisi (G7, FR-32/33) — GERCEK Postgres (Testcontainers) + Flyway V1..V4
 * (V4 team kolonu + outbox_events tablosunu ekler). Dogrular: SLA + team otomatik hesaplanir,
 * talep OPEN yazilir ve TicketOpened AYNI transaction'da outbox'a dusER (PENDING).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CreateTicketCommandHandler.class, TicketMapper.class, TicketOpeningPolicy.class,
        OutboxWriter.class, CreateTicketCommandHandlerIntegrationTest.TestJacksonConfig.class})
@Testcontainers(disabledWithoutDocker = true)
class CreateTicketCommandHandlerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** OutboxWriter tools.jackson ObjectMapper ister; @DataJpaTest bunu otomatik saglamaz. */
    @TestConfiguration
    static class TestJacksonConfig {
        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }
    }

    @Autowired
    CreateTicketCommandHandler handler;

    @Autowired
    TicketRepository ticketRepository;

    @Autowired
    OutboxRepository outboxRepository;

    @Test
    @DisplayName("talep acilis: HIGH -> 8s SLA, BILLING -> BILLING_TEAM, OPEN + TicketOpened outbox'a PENDING")
    void createComputesSlaTeamAndEnqueuesTicketOpened() {
        UUID customerId = UUID.randomUUID();
        Instant before = Instant.now();

        TicketResponse response = handler.handle(new CreateTicketCommand(customerId, "BILLING", "HIGH"));

        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.priority()).isEqualTo("HIGH");
        assertThat(response.team()).isEqualTo("BILLING_TEAM");
        // HIGH -> acilistan +8 saat (calisma anina gore tolerans).
        assertThat(response.slaDueAt())
                .isBetween(before.plus(Duration.ofHours(8)).minusSeconds(30),
                        Instant.now().plus(Duration.ofHours(8)).plusSeconds(30));

        assertThat(ticketRepository.findById(response.id()).orElseThrow().getTeam()).isEqualTo("BILLING_TEAM");

        assertThat(outboxRepository.findAll())
                .filteredOn(e -> e.getAggregateId().equals(response.id()))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getEventType()).isEqualTo("TicketOpened");
                    assertThat(e.getDestination()).isEqualTo(SagaTopics.TICKET_EVENTS);
                    assertThat(e.getStatus()).isEqualTo(OutboxStatus.PENDING);
                    assertThat(e.getPayload())
                            .contains(response.id().toString())
                            .contains(customerId.toString())
                            .contains("BILLING_TEAM");
                });
    }

    @Test
    @DisplayName("priority verilmezse MEDIUM varsayilir (24s SLA), bilinmeyen kategori GENERAL_TEAM'e duser")
    void createDefaultsPriorityAndRoutesUnknownCategoryToGeneral() {
        TicketResponse response = handler.handle(
                new CreateTicketCommand(UUID.randomUUID(), "baska-bir-konu", null));

        assertThat(response.priority()).isEqualTo("MEDIUM");
        assertThat(response.team()).isEqualTo("GENERAL_TEAM");
        assertThat(response.slaDueAt()).isAfter(Instant.now().plus(Duration.ofHours(23)));
    }
}
