package com.turkcell.productcatalogservice.application.features.tariff.command.create;

import java.math.BigDecimal;

import com.turkcell.commonlib.cqrs.Command;
import com.turkcell.productcatalogservice.dto.TariffResponse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Tarife olusturma komutu (HTTP request body). */
public record CreateTariffCommand(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String type,
        @NotNull @Positive BigDecimal monthlyFee,
        Integer minutesIncluded,
        Integer smsIncluded,
        Integer dataMbIncluded) implements Command<TariffResponse> {
}
