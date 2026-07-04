package com.turkcell.usageservice.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * quotas tablosunu karsilar (Flyway V4). Bir aboneligin BIR donemlik (takvim ayi)
 * kalan haklari. Kullanim dustukce *Remaining azalir (0'in altina inmez; fazlasi
 * OverageRecorded ile billing'e akar). *WarnedPct o donem yayinlanmis en yuksek
 * esiktir (0 -> 80 -> 100); ayni esik icin ikinci event uretilmez.
 */
@Entity
@Table(name = "quotas")
public class Quota {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID subscriptionId;
    private LocalDate periodStart;
    private LocalDate periodEnd;

    private BigDecimal minutesTotal;
    private BigDecimal smsTotal;
    private BigDecimal mbTotal;

    private BigDecimal minutesRemaining;
    private BigDecimal smsRemaining;
    private BigDecimal mbRemaining;

    private short minutesWarnedPct;
    private short smsWarnedPct;
    private short mbWarnedPct;

    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(UUID subscriptionId) { this.subscriptionId = subscriptionId; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
    public BigDecimal getMinutesTotal() { return minutesTotal; }
    public void setMinutesTotal(BigDecimal minutesTotal) { this.minutesTotal = minutesTotal; }
    public BigDecimal getSmsTotal() { return smsTotal; }
    public void setSmsTotal(BigDecimal smsTotal) { this.smsTotal = smsTotal; }
    public BigDecimal getMbTotal() { return mbTotal; }
    public void setMbTotal(BigDecimal mbTotal) { this.mbTotal = mbTotal; }
    public BigDecimal getMinutesRemaining() { return minutesRemaining; }
    public void setMinutesRemaining(BigDecimal minutesRemaining) { this.minutesRemaining = minutesRemaining; }
    public BigDecimal getSmsRemaining() { return smsRemaining; }
    public void setSmsRemaining(BigDecimal smsRemaining) { this.smsRemaining = smsRemaining; }
    public BigDecimal getMbRemaining() { return mbRemaining; }
    public void setMbRemaining(BigDecimal mbRemaining) { this.mbRemaining = mbRemaining; }
    public short getMinutesWarnedPct() { return minutesWarnedPct; }
    public void setMinutesWarnedPct(short minutesWarnedPct) { this.minutesWarnedPct = minutesWarnedPct; }
    public short getSmsWarnedPct() { return smsWarnedPct; }
    public void setSmsWarnedPct(short smsWarnedPct) { this.smsWarnedPct = smsWarnedPct; }
    public short getMbWarnedPct() { return mbWarnedPct; }
    public void setMbWarnedPct(short mbWarnedPct) { this.mbWarnedPct = mbWarnedPct; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
