package com.turkcell.orderservice.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
        @NotNull UUID customerId,
        @NotBlank String tariffCode) {
}
