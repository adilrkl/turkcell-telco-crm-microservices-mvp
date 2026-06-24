package com.turkcell.subscriptionservice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** sim_cards tablosunu karsilar (Flyway V1). Aktivasyonda olusturulup MSISDN'e baglanir. */
@Entity
@Table(name = "sim_cards")
public class SimCard {

    @Id
    private String iccid;
    private String imsi;
    private String msisdn;
    private String status;

    public String getIccid() { return iccid; }
    public void setIccid(String iccid) { this.iccid = iccid; }
    public String getImsi() { return imsi; }
    public void setImsi(String imsi) { this.imsi = imsi; }
    public String getMsisdn() { return msisdn; }
    public void setMsisdn(String msisdn) { this.msisdn = msisdn; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
