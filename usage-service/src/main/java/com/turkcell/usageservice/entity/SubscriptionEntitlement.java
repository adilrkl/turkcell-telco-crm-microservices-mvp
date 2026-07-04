package com.turkcell.usageservice.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * subscription_entitlements tablosunu karsilar (Flyway V4). OrderConfirmed'den
 * yazilan tarife hak fotografi: yeni donemin kotasi katalog'a senkron cagri
 * yapmadan buradan acilir. customer_id/msisdn esik SMS event'inde tasinir.
 */
@Entity
@Table(name = "subscription_entitlements")
public class SubscriptionEntitlement {

    @Id
    private UUID subscriptionId;

    private UUID customerId;
    private String msisdn;
    private String tariffCode;
    private Integer minutesIncluded;
    private Integer smsIncluded;
    private Integer mbIncluded;
    private Instant createdAt;

    public UUID getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(UUID subscriptionId) { this.subscriptionId = subscriptionId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public String getMsisdn() { return msisdn; }
    public void setMsisdn(String msisdn) { this.msisdn = msisdn; }
    public String getTariffCode() { return tariffCode; }
    public void setTariffCode(String tariffCode) { this.tariffCode = tariffCode; }
    public Integer getMinutesIncluded() { return minutesIncluded; }
    public void setMinutesIncluded(Integer minutesIncluded) { this.minutesIncluded = minutesIncluded; }
    public Integer getSmsIncluded() { return smsIncluded; }
    public void setSmsIncluded(Integer smsIncluded) { this.smsIncluded = smsIncluded; }
    public Integer getMbIncluded() { return mbIncluded; }
    public void setMbIncluded(Integer mbIncluded) { this.mbIncluded = mbIncluded; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
