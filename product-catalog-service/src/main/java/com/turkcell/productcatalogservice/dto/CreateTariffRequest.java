package com.turkcell.productcatalogservice.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateTariffRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String type,
        @NotNull @Positive BigDecimal monthlyFee,
        Integer minutesIncluded,
        Integer smsIncluded,
        Integer dataMbIncluded) {
}
