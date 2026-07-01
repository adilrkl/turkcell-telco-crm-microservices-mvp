package com.turkcell.orderservice.controller;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.turkcell.commonlib.cqrs.Mediator;
import com.turkcell.commonlib.dto.ApiResponse;
import com.turkcell.orderservice.application.features.order.command.place.PlaceOrderCommand;
import com.turkcell.orderservice.application.features.order.query.get.GetOrderQuery;
import com.turkcell.orderservice.dto.OrderResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final Mediator mediator;

    public OrderController(Mediator mediator) {
        this.mediator = mediator;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','CSR')")
    public ApiResponse<OrderResponse> place(@Valid @RequestBody PlaceOrderCommand command) {
        return ApiResponse.ok(mediator.send(command), "Siparis alindi");
    }

    /** Saga asenkron ilerledigi icin son durumu (FULFILLED/CANCELLED) buradan poll et. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','CSR')")
    public ApiResponse<OrderResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(mediator.send(new GetOrderQuery(id)), "Siparis durumu");
    }
}
