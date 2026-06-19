package com.turkcell.customerservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.turkcell.commonlib.dto.ApiResponse;
import com.turkcell.customerservice.dto.CustomerResponse;
import com.turkcell.customerservice.service.CustomerService;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    // Kimlik dogrulanmis her cagri (order-service Feign dahil) musteri okuyabilir.
    @GetMapping("/{id}")
    public ApiResponse<CustomerResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(customerService.getById(id));
    }

    // Tum musteri listesi yalnizca CSR/ADMIN.
    @GetMapping
    @PreAuthorize("hasAnyRole('CSR','ADMIN')")
    public ApiResponse<List<CustomerResponse>> list() {
        return ApiResponse.ok(customerService.findAll());
    }
}
