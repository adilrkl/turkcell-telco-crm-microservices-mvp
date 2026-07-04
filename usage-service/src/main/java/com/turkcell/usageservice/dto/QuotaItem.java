package com.turkcell.usageservice.dto;

import java.math.BigDecimal;

/** Kota kaleminin tip bazli goruntusu. type: VOICE (dakika) | SMS (adet) | DATA (MB). */
public record QuotaItem(
        String type,
        BigDecimal total,
        BigDecimal remaining,
        int usedPct) {
}
