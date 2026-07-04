package com.turkcell.ticketservice.dto;

import java.time.Instant;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        UUID customerId,
        String category,
        String priority,
        String status,
        String team,
        UUID assignedTo,
        Instant slaDueAt,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt) {
}
