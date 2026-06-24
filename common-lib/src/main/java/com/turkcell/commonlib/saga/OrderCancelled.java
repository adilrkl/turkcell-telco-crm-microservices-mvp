package com.turkcell.commonlib.saga;

import java.util.UUID;

/**
 * Domain event: order -> dis dunya ({@link SagaTopics#ORDER_EVENTS}).
 * Saga basarisiz olup compensation tamamlandiginda (order CANCELLED) yayinlanir.
 * notification basarisizlik mesaji atar.
 */
public record OrderCancelled(
        UUID eventId,
        UUID orderId,
        UUID customerId,
        String reason) {
}
