package com.turkcell.productcatalogservice.application.features.tariff.query.getbycode;

import com.turkcell.commonlib.cqrs.Query;
import com.turkcell.productcatalogservice.dto.TariffResponse;

/** Koda gore tekil tarife sorgusu (Redis cache'li). */
public record GetTariffByCodeQuery(String code) implements Query<TariffResponse> {
}
