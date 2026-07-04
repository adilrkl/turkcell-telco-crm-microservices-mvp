package com.turkcell.usageservice.entity;

/** Outbox satiri yasam dongusu: PENDING -> SENT | FAILED (3 deneme sonrasi). */
public enum OutboxStatus {
    PENDING, SENT, FAILED
}
