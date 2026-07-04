package com.turkcell.billingservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.billingservice.entity.PendingCharge;
import com.turkcell.billingservice.entity.ProcessedEvent;
import com.turkcell.billingservice.repository.PendingChargeRepository;
import com.turkcell.billingservice.repository.ProcessedEventRepository;
import com.turkcell.commonlib.saga.OverageRecorded;

/**
 * OverageRecorded isleyicisi (G1 / FR-20): kota asan kullanimi bekleyen asim ucreti
 * (pending_charges) olarak biriktirir; bir sonraki bill-run faturaya kalem ekler.
 * Birim fiyatlar config'ten gelir ve ingest aninda dondurulur (sonradan degisen
 * tarife eski asimlari etkilemez). Inbox idempotency + yazim tek transaction.
 */
@Service
public class OverageEventHandler {

    private static final Logger log = LoggerFactory.getLogger(OverageEventHandler.class);

    private final ProcessedEventRepository processedEventRepository;
    private final PendingChargeRepository pendingChargeRepository;
    private final BigDecimal voicePerMinute;
    private final BigDecimal smsPerUnit;
    private final BigDecimal dataPerMb;

    public OverageEventHandler(ProcessedEventRepository processedEventRepository,
                               PendingChargeRepository pendingChargeRepository,
                               @Value("${billing.overage.voice-per-minute:0.50}") BigDecimal voicePerMinute,
                               @Value("${billing.overage.sms-per-unit:0.25}") BigDecimal smsPerUnit,
                               @Value("${billing.overage.data-per-mb:0.05}") BigDecimal dataPerMb) {
        this.processedEventRepository = processedEventRepository;
        this.pendingChargeRepository = pendingChargeRepository;
        this.voicePerMinute = voicePerMinute;
        this.smsPerUnit = smsPerUnit;
        this.dataPerMb = dataPerMb;
    }

    @Transactional
    public void handle(OverageRecorded event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("billing: event zaten islendi, atlaniyor. eventId={}", event.eventId());
            return;
        }

        BigDecimal unitPrice = unitPriceOf(event.type());
        if (unitPrice == null) {
            log.warn("billing: bilinmeyen asim tipi, atlaniyor. type={} eventId={}", event.type(), event.eventId());
            markProcessed(event);
            return;
        }

        PendingCharge charge = new PendingCharge();
        charge.setSubscriptionId(event.subscriptionId());
        charge.setCustomerId(event.customerId());
        charge.setType(event.type());
        charge.setQuantity(event.quantity());
        charge.setUnitPrice(unitPrice);
        charge.setAmount(event.quantity().multiply(unitPrice).setScale(2, RoundingMode.HALF_UP));
        charge.setDescription("Asim - " + event.type() + " " + event.quantity().stripTrailingZeros().toPlainString()
                + " " + unitOf(event.type()) + " (" + event.periodStart() + " / " + event.periodEnd() + ")");
        charge.setStatus("PENDING");
        charge.setCreatedAt(Instant.now());
        pendingChargeRepository.save(charge);

        markProcessed(event);
        log.info("billing: asim ucreti biriktirildi. sub={} type={} qty={} tutar={}",
                event.subscriptionId(), event.type(), event.quantity(), charge.getAmount());
    }

    private void markProcessed(OverageRecorded event) {
        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(event.eventId());
        processed.setProcessedAt(Instant.now());
        processedEventRepository.save(processed);
    }

    private BigDecimal unitPriceOf(String type) {
        return switch (type) {
            case "VOICE" -> voicePerMinute;
            case "SMS" -> smsPerUnit;
            case "DATA" -> dataPerMb;
            default -> null;
        };
    }

    static String unitOf(String type) {
        return switch (type) {
            case "VOICE" -> "dk";
            case "SMS" -> "adet";
            case "DATA" -> "MB";
            default -> "";
        };
    }
}
