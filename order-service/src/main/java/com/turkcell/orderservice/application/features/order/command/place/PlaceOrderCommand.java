package com.turkcell.orderservice.application.features.order.command.place;

import java.util.UUID;

import com.turkcell.commonlib.cqrs.Command;
import com.turkcell.orderservice.dto.OrderResponse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Siparis olusturma komutu (HTTP request body). */
public record PlaceOrderCommand(
        @NotNull UUID customerId,
        @NotBlank String tariffCode) implements Command<OrderResponse> {
}
