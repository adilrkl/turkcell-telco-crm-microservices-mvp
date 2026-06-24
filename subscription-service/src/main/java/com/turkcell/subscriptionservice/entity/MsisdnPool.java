package com.turkcell.subscriptionservice.entity;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * msisdn_pool tablosunu karsilar (Flyway V1). Numara yasam dongusu: FREE -> RESERVED -> ALLOCATED.
 * Compensation'da ALLOCATED/RESERVED -> FREE. reservedUntil rezervasyonun son kullanma anidir.
 */
@Entity
@Table(name = "msisdn_pool")
public class MsisdnPool {

    @Id
    private String msisdn;
    private String status;
    private Instant reservedUntil;

    public String getMsisdn() { return msisdn; }
    public void setMsisdn(String msisdn) { this.msisdn = msisdn; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getReservedUntil() { return reservedUntil; }
    public void setReservedUntil(Instant reservedUntil) { this.reservedUntil = reservedUntil; }
}
