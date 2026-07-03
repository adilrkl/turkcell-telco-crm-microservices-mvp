package com.turkcell.orderservice.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.turkcell.orderservice.client.dto.CustomerEnvelope;

/** customer-service'e senkron cagri (Eureka service-id ile load-balanced, circuit breaker'li). */
@FeignClient(name = "customer-service", fallbackFactory = CustomerClientFallbackFactory.class)
public interface CustomerClient {

    @GetMapping("/api/customers/{id}")
    CustomerEnvelope getById(@PathVariable("id") UUID id);
}
