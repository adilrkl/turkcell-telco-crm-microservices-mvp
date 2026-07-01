package com.turkcell.productcatalogservice.application.features.tariff.query.list;

import org.springframework.data.domain.Pageable;

import com.turkcell.commonlib.cache.RestPage;
import com.turkcell.commonlib.cqrs.Query;
import com.turkcell.productcatalogservice.dto.TariffResponse;

/** Sayfali tarife listesi sorgusu (Redis cache'li). */
public record ListTariffsQuery(Pageable pageable) implements Query<RestPage<TariffResponse>> {
}
