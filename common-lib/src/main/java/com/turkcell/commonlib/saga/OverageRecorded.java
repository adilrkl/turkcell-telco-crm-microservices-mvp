package com.turkcell.commonlib.saga;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain event: usage -> billing ({@link SagaTopics#QUOTA_EVENTS}).
 * Kota TUKENDIKTEN sonra gelen kullanim fazlasini tasir; billing bunu bekleyen
 * asim ucreti (pending_charges) olarak biriktirir ve bir sonraki fatura kesiminde
 * kalem olarak ekler. Birim fiyatlama billing tarafindadir (config).
 *
 * @param type     VOICE | SMS | DATA
 * @param quantity kota asan miktar (dakika / adet / MB)
 */
public record OverageRecorded(
        UUID eventId,
        UUID subscriptionId,
        UUID customerId,
        String type,
        BigDecimal quantity,
        LocalDate periodStart,
        LocalDate periodEnd) {
}
