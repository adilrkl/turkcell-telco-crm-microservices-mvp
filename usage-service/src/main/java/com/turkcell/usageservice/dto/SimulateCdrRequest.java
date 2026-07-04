package com.turkcell.usageservice.dto;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** CDR simulatoru istegi: verilen abonelik icin count adet rastgele kullanim event'i uret. */
public record SimulateCdrRequest(
        @NotNull UUID subscriptionId,
        @Min(1) @Max(500) Integer count) {

    public int countOrDefault() {
        return count != null ? count : 10;
    }
}
