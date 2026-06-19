package com.turkcell.billingservice.entity;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** bill_cycles tablosunu karsilar (Flyway V1). */
@Entity
@Table(name = "bill_cycles")
public class BillCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID customerId;
    private Short dayOfMonth;
    private LocalDate nextRunDate;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public Short getDayOfMonth() { return dayOfMonth; }
    public void setDayOfMonth(Short dayOfMonth) { this.dayOfMonth = dayOfMonth; }
    public LocalDate getNextRunDate() { return nextRunDate; }
    public void setNextRunDate(LocalDate nextRunDate) { this.nextRunDate = nextRunDate; }
}
