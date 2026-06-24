package com.turkcell.orderservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Siparis cevabi. Saga asenkron ilerledigi icin POST aninda status genelde PENDING_PAYMENT'tir;
 * son durum (FULFILLED/CANCELLED) icin GET /api/orders/{id} ile poll edilir.
 */
public record OrderResponse(
        UUID orderId,
        UUID customerId,
        String status,
        BigDecimal totalAmount,
        String currency,
        String tariffCode) {
}
