package com.turkcell.usageservice.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Bir aboneligin icinde bulunulan donem kota goruntusu (FE kota karti / FR-19). */
public record QuotaResponse(
        UUID subscriptionId,
        LocalDate periodStart,
        LocalDate periodEnd,
        List<QuotaItem> items) {
}
