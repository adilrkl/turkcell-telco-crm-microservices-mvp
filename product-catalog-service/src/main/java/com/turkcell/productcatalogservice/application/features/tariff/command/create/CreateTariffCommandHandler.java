package com.turkcell.productcatalogservice.application.features.tariff.command.create;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;

import com.turkcell.commonlib.cqrs.CommandHandler;
import com.turkcell.productcatalogservice.application.features.tariff.mapper.TariffMapper;
import com.turkcell.productcatalogservice.dto.TariffResponse;
import com.turkcell.productcatalogservice.entity.Tariff;
import com.turkcell.productcatalogservice.repository.TariffRepository;

/** Yeni tarife yazar ve ilgili Redis cache alanlarini temizler. */
@Component
public class CreateTariffCommandHandler implements CommandHandler<CreateTariffCommand, TariffResponse> {

    private final TariffRepository repository;
    private final TariffMapper mapper;

    public CreateTariffCommandHandler(TariffRepository repository, TariffMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "tariffByCode", allEntries = true),
            @CacheEvict(value = "tariffPage", allEntries = true)
    })
    public TariffResponse handle(CreateTariffCommand command) {
        Tariff saved = repository.save(mapper.toTariff(command));
        return mapper.toResponse(saved);
    }
}
