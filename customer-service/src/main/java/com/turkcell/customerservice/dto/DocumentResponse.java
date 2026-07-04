package com.turkcell.customerservice.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID customerId,
        String type,
        String fileRef,
        Instant verifiedAt) {
}
