package com.turkcell.ticketservice.saga;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.turkcell.ticketservice.entity.OutboxEvent;
import com.turkcell.ticketservice.entity.OutboxStatus;
import com.turkcell.ticketservice.repository.OutboxRepository;

import tools.jackson.databind.ObjectMapper;

/** Domain event'leri transactional outbox'a yazar (is degisikligi ile ayni transaction'da). */
@Component
public class OutboxWriter {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void enqueue(String destination, String eventType, UUID aggregateId, Object payload) {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setAggregateType("Ticket");
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setDestination(destination);
        event.setPayload(objectMapper.writeValueAsString(payload));
        event.setStatus(OutboxStatus.PENDING);
        event.setRetryCount(0);
        event.setCreatedAt(Instant.now());
        outboxRepository.save(event);
    }
}
