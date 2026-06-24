package com.turkcell.orderservice.saga;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.turkcell.orderservice.entity.OutboxEvent;
import com.turkcell.orderservice.entity.OutboxStatus;
import com.turkcell.orderservice.repository.OutboxRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Transactional outbox'a satir yazar. Cagiranin transaction'i icinde calisir
 * (domain degisikligi + outbox satiri ayni commit'te); OutboxPoller sonra publish eder.
 * 'destination' = hedef Kafka topic'i, 'eventType' = payload tipi (consumer dispatch icin).
 */
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
        event.setAggregateType("Order");
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
