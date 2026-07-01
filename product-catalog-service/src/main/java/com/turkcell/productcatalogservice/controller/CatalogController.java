package com.turkcell.productcatalogservice.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.turkcell.commonlib.cache.RestPage;
import com.turkcell.commonlib.cqrs.Mediator;
import com.turkcell.commonlib.dto.ApiResponse;
import com.turkcell.productcatalogservice.application.features.tariff.command.create.CreateTariffCommand;
import com.turkcell.productcatalogservice.application.features.tariff.query.getbycode.GetTariffByCodeQuery;
import com.turkcell.productcatalogservice.application.features.tariff.query.list.ListTariffsQuery;
import com.turkcell.productcatalogservice.dto.TariffResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/catalog/tariffs")
public class CatalogController {

    private final Mediator mediator;

    public CatalogController(Mediator mediator) {
        this.mediator = mediator;
    }

    // Her kimlik dogrulanmis kullanici tarifeleri okuyabilir (Redis cache'li).
    @GetMapping("/{code}")
    public ApiResponse<TariffResponse> getByCode(@PathVariable String code) {
        return ApiResponse.ok(mediator.send(new GetTariffByCodeQuery(code)));
    }

    @GetMapping
    public ApiResponse<RestPage<TariffResponse>> list(Pageable pageable) {
        return ApiResponse.ok(mediator.send(new ListTariffsQuery(pageable)));
    }

    // Yalnizca CATALOG_ADMIN tarife olusturabilir.
    @PostMapping
    @PreAuthorize("hasRole('CATALOG_ADMIN')")
    public ApiResponse<TariffResponse> create(@Valid @RequestBody CreateTariffCommand command) {
        return ApiResponse.ok(mediator.send(command), "Tarife olusturuldu");
    }
}
