package com.turkcell.commonlib.saga;

import java.util.UUID;

/**
 * Domain event: customer -> dis dunya ({@link SagaTopics#CUSTOMER_EVENTS}).
 * KYC onayi ile musteri PENDING -> ACTIVE oldugunda yayinlanir (G3, docx senaryo 14.1);
 * notification "hos geldiniz / hesabiniz aktif" bildirimi atar.
 */
public record CustomerKYCApproved(
        UUID eventId,
        UUID customerId,
        String firstName,
        String lastName) {
}
