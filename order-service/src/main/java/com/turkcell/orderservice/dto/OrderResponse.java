package com.turkcell.orderservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        UUID customerId,
        String status,
        BigDecimal totalAmount,
        String currency,
        String tariffCode,
        UUID eventId) {
}
