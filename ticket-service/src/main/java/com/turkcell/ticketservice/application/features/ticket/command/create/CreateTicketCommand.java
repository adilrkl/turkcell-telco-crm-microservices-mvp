package com.turkcell.ticketservice.application.features.ticket.command.create;

import java.util.UUID;

import com.turkcell.commonlib.cqrs.Command;
import com.turkcell.ticketservice.dto.TicketResponse;

/**
 * Yeni destek talebi olusturma komutu (status OPEN ile baslar). slaDueAt + team
 * client'tan alinmaz; acilista otomasyon hesaplar (G7, FR-32).
 */
public record CreateTicketCommand(
        UUID customerId,
        String category,
        String priority) implements Command<TicketResponse> {
}
