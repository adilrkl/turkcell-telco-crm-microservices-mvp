package com.turkcell.orderservice.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * outbox_events tablosunu karsilar (Flyway V1 + V2). Transactional Outbox satiri:
 * domain degisikligi ile AYNI transaction'da yazilir, OutboxPoller daha sonra
 * Kafka'ya publish edip status = SENT yapar.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    private UUID aggregateId;     // ornek: orderId
    private String aggregateType; // ornek: "Order"
    private String eventType;     // ornek: "ReserveMsisdnCommand"
    private String destination;   // hedef Kafka topic (subscription-commands / payment-commands / order-events)

    @Column(columnDefinition = "text")
    private String payload;       // serialize edilmis JSON event

    private String errorMessage;
    private int retryCount;
    private Instant createdAt;
    private Instant processedAt;
    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    private OutboxStatus status;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAggregateId() { return aggregateId; }
    public void setAggregateId(UUID aggregateId) { this.aggregateId = aggregateId; }
    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public OutboxStatus getStatus() { return status; }
    public void setStatus(OutboxStatus status) { this.status = status; }
}
