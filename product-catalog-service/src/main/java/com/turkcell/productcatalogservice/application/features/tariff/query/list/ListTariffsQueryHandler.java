package com.turkcell.productcatalogservice.application.features.tariff.query.list;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.turkcell.commonlib.cache.RestPage;
import com.turkcell.commonlib.cqrs.QueryHandler;
import com.turkcell.productcatalogservice.application.features.tariff.mapper.TariffMapper;
import com.turkcell.productcatalogservice.dto.TariffResponse;
import com.turkcell.productcatalogservice.repository.TariffRepository;

@Component
public class ListTariffsQueryHandler implements QueryHandler<ListTariffsQuery, RestPage<TariffResponse>> {

    private final TariffRepository repository;
    private final TariffMapper mapper;

    public ListTariffsQueryHandler(TariffRepository repository, TariffMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Cacheable(value = "tariffPage", key = "'p=' + #query.pageable.pageNumber + ';s=' + #query.pageable.pageSize")
    public RestPage<TariffResponse> handle(ListTariffsQuery query) {
        return new RestPage<>(repository.findAll(query.pageable()).map(mapper::toResponse));
    }
}
