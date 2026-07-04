package com.turkcell.customerservice.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * documents tablosunu karsilar (Flyway V1). KYC belgesi; dosyanin kendisi saklanmaz,
 * fileRef mock bir isaretcidir (object-storage tasima isi Faz 6 / MinIO).
 * verifiedAt, KYC onayi aninda damgalanir.
 */
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID customerId;
    private String type;      // ID_CARD | PASSPORT | DRIVER_LICENSE | OTHER
    private String fileRef;
    private Instant verifiedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getFileRef() { return fileRef; }
    public void setFileRef(String fileRef) { this.fileRef = fileRef; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
}
