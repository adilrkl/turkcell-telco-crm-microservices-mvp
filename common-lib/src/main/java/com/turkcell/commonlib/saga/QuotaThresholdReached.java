package com.turkcell.commonlib.saga;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain event: usage -> dis dunya ({@link SagaTopics#QUOTA_EVENTS}).
 * Bir kota kaleminin donem kullanimi %80 veya %100 esigini GECTIGI anda yayinlanir
 * (esik basina en fazla bir kez; notification SMS atar).
 *
 * @param type         VOICE | SMS | DATA
 * @param thresholdPct gecilen esik: 80 veya 100
 */
public record QuotaThresholdReached(
        UUID eventId,
        UUID subscriptionId,
        UUID customerId,
        String msisdn,
        String type,
        int thresholdPct,
        BigDecimal remaining,
        BigDecimal total,
        LocalDate periodStart,
        LocalDate periodEnd) {
}
