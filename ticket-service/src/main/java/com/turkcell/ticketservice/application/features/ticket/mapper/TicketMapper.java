package com.turkcell.ticketservice.application.features.ticket.mapper;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.turkcell.ticketservice.application.features.ticket.command.create.CreateTicketCommand;
import com.turkcell.ticketservice.dto.TicketCommentResponse;
import com.turkcell.ticketservice.dto.TicketDetailResponse;
import com.turkcell.ticketservice.dto.TicketResponse;
import com.turkcell.ticketservice.entity.Ticket;
import com.turkcell.ticketservice.entity.TicketComment;

/** Ticket/TicketComment entity <-> command/response donusumleri. */
@Component
public class TicketMapper {

    /**
     * Yeni OPEN talep olusturur. priority/team/slaDueAt acilista otomasyonca hesaplanir
     * (G7); handler bunlari cozup buraya verir.
     */
    public Ticket toNewTicket(CreateTicketCommand command, String priority, String team, Instant slaDueAt) {
        Ticket ticket = new Ticket();
        ticket.setCustomerId(command.customerId());
        ticket.setCategory(command.category());
        ticket.setPriority(priority);
        ticket.setTeam(team);
        ticket.setSlaDueAt(slaDueAt);
        return ticket;
    }

    public TicketResponse toResponse(Ticket t) {
        return new TicketResponse(
                t.getId(),
                t.getCustomerId(),
                t.getCategory(),
                t.getPriority(),
                t.getStatus(),
                t.getTeam(),
                t.getAssignedTo(),
                t.getSlaDueAt(),
                t.getCreatedAt(),
                t.getUpdatedAt(),
                t.getResolvedAt());
    }

    public TicketCommentResponse toCommentResponse(TicketComment c) {
        return new TicketCommentResponse(c.getId(), c.getAuthorId(), c.getBody(), c.getCreatedAt());
    }

    public TicketDetailResponse toDetail(Ticket ticket, List<TicketComment> comments) {
        return new TicketDetailResponse(
                toResponse(ticket),
                comments.stream().map(this::toCommentResponse).toList());
    }
}
