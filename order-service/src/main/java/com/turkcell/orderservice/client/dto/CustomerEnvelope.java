package com.turkcell.orderservice.client.dto;

import java.util.UUID;

/** customer-service ApiResponse zarfinin Feign tarafindaki karsiligi (sadece ihtiyac duyulan alanlar). */
public record CustomerEnvelope(CustomerView data) {

    public record CustomerView(UUID id, String type, String firstName, String lastName, String status) {
    }
}
