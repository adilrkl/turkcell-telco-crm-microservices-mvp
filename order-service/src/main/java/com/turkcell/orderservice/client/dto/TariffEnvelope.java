package com.turkcell.orderservice.client.dto;

import java.math.BigDecimal;

/** product-catalog ApiResponse zarfinin Feign tarafindaki karsiligi. */
public record TariffEnvelope(TariffView data) {

    public record TariffView(String code, String name, BigDecimal monthlyFee, String status) {
    }
}
