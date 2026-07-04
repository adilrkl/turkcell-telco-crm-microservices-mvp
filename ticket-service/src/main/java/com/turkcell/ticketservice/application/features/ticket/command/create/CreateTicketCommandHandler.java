package com.turkcell.ticketservice.application.features.ticket.command.create;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.cqrs.CommandHandler;
import com.turkcell.commonlib.saga.SagaTopics;
import com.turkcell.commonlib.saga.TicketOpened;
import com.turkcell.ticketservice.application.features.ticket.mapper.TicketMapper;
import com.turkcell.ticketservice.application.features.ticket.rule.TicketOpeningPolicy;
import com.turkcell.ticketservice.dto.TicketResponse;
import com.turkcell.ticketservice.entity.Ticket;
import com.turkcell.ticketservice.repository.TicketRepository;
import com.turkcell.ticketservice.saga.OutboxWriter;

/**
 * Talep acilis akisi (G7, FR-32/33): oncelige gore SLA vadesi + kategoriye gore ekip
 * yonlendirmesi otomatik hesaplanir, talep OPEN yazilir ve TicketOpened event'i AYNI
 * transaction'da outbox'a yazilir (notification "talebiniz alindi" SMS'i atar).
 */
@Component
public class CreateTicketCommandHandler implements CommandHandler<CreateTicketCommand, TicketResponse> {

    private static final Logger log = LoggerFactory.getLogger(CreateTicketCommandHandler.class);
    private static final String DEFAULT_PRIORITY = "MEDIUM";

    private final TicketRepository repository;
    private final TicketMapper mapper;
    private final TicketOpeningPolicy openingPolicy;
    private final OutboxWriter outbox;

    public CreateTicketCommandHandler(TicketRepository repository,
                                      TicketMapper mapper,
                                      TicketOpeningPolicy openingPolicy,
                                      OutboxWriter outbox) {
        this.repository = repository;
        this.mapper = mapper;
        this.openingPolicy = openingPolicy;
        this.outbox = outbox;
    }

    @Override
    @Transactional
    public TicketResponse handle(CreateTicketCommand command) {
        String priority = command.priority() != null ? command.priority() : DEFAULT_PRIORITY;
        String team = openingPolicy.routeTeam(command.category());
        Instant slaDueAt = openingPolicy.slaDueAt(priority, Instant.now());

        Ticket saved = repository.save(mapper.toNewTicket(command, priority, team, slaDueAt));

        outbox.enqueue(SagaTopics.TICKET_EVENTS, "TicketOpened", saved.getId(),
                new TicketOpened(UUID.randomUUID(), saved.getId(), saved.getCustomerId(),
                        saved.getCategory(), saved.getPriority(), saved.getTeam(), saved.getSlaDueAt()));

        log.info("ticket: acildi. id={} category={} priority={} team={} slaDueAt={}",
                saved.getId(), saved.getCategory(), saved.getPriority(), saved.getTeam(), saved.getSlaDueAt());
        return mapper.toResponse(saved);
    }
}
