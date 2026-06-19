package com.turkcell.orderservice.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.turkcell.commonlib.dto.ApiResponse;
import com.turkcell.orderservice.dto.CreateOrderRequest;
import com.turkcell.orderservice.dto.OrderResponse;
import com.turkcell.orderservice.service.OrderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','CSR')")
    public ApiResponse<OrderResponse> place(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.ok(orderService.placeOrder(request), "Siparis alindi");
    }
}
