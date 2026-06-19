package com.turkcell.productcatalogservice.service;

import java.time.LocalDate;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.turkcell.commonlib.cache.RestPage;
import com.turkcell.commonlib.exception.ResourceNotFoundException;
import com.turkcell.productcatalogservice.dto.CreateTariffRequest;
import com.turkcell.productcatalogservice.dto.TariffResponse;
import com.turkcell.productcatalogservice.entity.Tariff;
import com.turkcell.productcatalogservice.repository.TariffRepository;

@Service
public class TariffServiceImpl implements TariffService {

    private final TariffRepository repository;

    public TariffServiceImpl(TariffRepository repository) {
        this.repository = repository;
    }

    @Override
    @Cacheable(value = "tariffByCode", key = "#code")
    public TariffResponse getByCode(String code) {
        Tariff tariff = repository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Tariff", code));
        return toResponse(tariff);
    }

    @Override
    @Cacheable(value = "tariffPage", key = "'p=' + #pageable.pageNumber + ';s=' + #pageable.pageSize")
    public RestPage<TariffResponse> list(Pageable pageable) {
        return new RestPage<>(repository.findAll(pageable).map(TariffServiceImpl::toResponse));
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "tariffByCode", allEntries = true),
            @CacheEvict(value = "tariffPage", allEntries = true)
    })
    public TariffResponse create(CreateTariffRequest request) {
        Tariff tariff = new Tariff();
        tariff.setCode(request.code());
        tariff.setName(request.name());
        tariff.setType(request.type());
        tariff.setMonthlyFee(request.monthlyFee());
        tariff.setMinutesIncluded(request.minutesIncluded());
        tariff.setSmsIncluded(request.smsIncluded());
        tariff.setDataMbIncluded(request.dataMbIncluded());
        tariff.setStatus("ACTIVE");
        tariff.setEffectiveFrom(LocalDate.now());
        return toResponse(repository.save(tariff));
    }

    private static TariffResponse toResponse(Tariff t) {
        return new TariffResponse(t.getId(), t.getCode(), t.getName(), t.getType(), t.getMonthlyFee(),
                t.getMinutesIncluded(), t.getSmsIncluded(), t.getDataMbIncluded(), t.getStatus());
    }
}
