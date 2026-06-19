package com.turkcell.productcatalogservice.service;

import org.springframework.data.domain.Pageable;

import com.turkcell.commonlib.cache.RestPage;
import com.turkcell.productcatalogservice.dto.CreateTariffRequest;
import com.turkcell.productcatalogservice.dto.TariffResponse;

public interface TariffService {
    TariffResponse getByCode(String code);
    RestPage<TariffResponse> list(Pageable pageable);
    TariffResponse create(CreateTariffRequest request);
}
