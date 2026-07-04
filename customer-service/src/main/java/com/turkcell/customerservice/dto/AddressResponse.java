package com.turkcell.customerservice.dto;

import java.util.UUID;

public record AddressResponse(
        UUID id,
        UUID customerId,
        String line1,
        String city,
        String district,
        String postalCode,
        boolean isDefault) {
}
