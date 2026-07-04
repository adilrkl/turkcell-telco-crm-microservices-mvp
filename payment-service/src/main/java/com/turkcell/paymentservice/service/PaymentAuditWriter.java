package com.turkcell.paymentservice.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.turkcell.paymentservice.entity.AuditLog;
import com.turkcell.paymentservice.repository.AuditLogRepository;

/** audit_log yazim yardimcisi (payment-service). Cagiranin transaction'inda calisir. */
@Component
public class PaymentAuditWriter {

    private final AuditLogRepository auditLogRepository;

    public PaymentAuditWriter(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void write(String entityName, UUID entityId, String action, String detail) {
        AuditLog a = new AuditLog();
        a.setServiceName("payment-service");
        a.setEntityName(entityName);
        a.setEntityId(entityId);
        a.setAction(action);
        a.setDetail(detail);
        a.setCreatedAt(Instant.now());
        auditLogRepository.save(a);
    }
}
