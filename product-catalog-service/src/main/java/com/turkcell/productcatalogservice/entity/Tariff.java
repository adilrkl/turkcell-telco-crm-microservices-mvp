package com.turkcell.productcatalogservice.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** tariffs tablosunu karsilar (Flyway V1). */
@Entity
@Table(name = "tariffs")
public class Tariff {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String code;
    private String name;
    private String type;
    private BigDecimal monthlyFee;
    private Integer minutesIncluded;
    private Integer smsIncluded;
    private Integer dataMbIncluded;
    private String status;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public BigDecimal getMonthlyFee() { return monthlyFee; }
    public void setMonthlyFee(BigDecimal monthlyFee) { this.monthlyFee = monthlyFee; }
    public Integer getMinutesIncluded() { return minutesIncluded; }
    public void setMinutesIncluded(Integer minutesIncluded) { this.minutesIncluded = minutesIncluded; }
    public Integer getSmsIncluded() { return smsIncluded; }
    public void setSmsIncluded(Integer smsIncluded) { this.smsIncluded = smsIncluded; }
    public Integer getDataMbIncluded() { return dataMbIncluded; }
    public void setDataMbIncluded(Integer dataMbIncluded) { this.dataMbIncluded = dataMbIncluded; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
}
