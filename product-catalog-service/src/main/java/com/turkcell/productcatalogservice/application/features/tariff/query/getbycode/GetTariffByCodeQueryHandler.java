package com.turkcell.productcatalogservice.application.features.tariff.query.getbycode;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.turkcell.commonlib.cqrs.QueryHandler;
import com.turkcell.commonlib.exception.ResourceNotFoundException;
import com.turkcell.productcatalogservice.application.features.tariff.mapper.TariffMapper;
import com.turkcell.productcatalogservice.dto.TariffResponse;
import com.turkcell.productcatalogservice.entity.Tariff;
import com.turkcell.productcatalogservice.repository.TariffRepository;

@Component
public class GetTariffByCodeQueryHandler implements QueryHandler<GetTariffByCodeQuery, TariffResponse> {

    private final TariffRepository repository;
    private final TariffMapper mapper;

    public GetTariffByCodeQueryHandler(TariffRepository repository, TariffMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Cacheable(value = "tariffByCode", key = "#query.code")
    public TariffResponse handle(GetTariffByCodeQuery query) {
        Tariff tariff = repository.findByCode(query.code())
                .orElseThrow(() -> new ResourceNotFoundException("Tariff", query.code()));
        return mapper.toResponse(tariff);
    }
}
