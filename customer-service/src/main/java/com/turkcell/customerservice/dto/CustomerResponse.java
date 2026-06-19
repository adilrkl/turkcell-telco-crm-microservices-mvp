package com.turkcell.customerservice.dto;

import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String type,
        String firstName,
        String lastName,
        String status) {
}
