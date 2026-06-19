package com.turkcell.productcatalogservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TariffResponse(
        UUID id,
        String code,
        String name,
        String type,
        BigDecimal monthlyFee,
        Integer minutesIncluded,
        Integer smsIncluded,
        Integer dataMbIncluded,
        String status) {
}
