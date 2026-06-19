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
import com.turkcell.commonlib.dto.ApiResponse;
import com.turkcell.productcatalogservice.dto.CreateTariffRequest;
import com.turkcell.productcatalogservice.dto.TariffResponse;
import com.turkcell.productcatalogservice.service.TariffService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/catalog/tariffs")
public class CatalogController {

    private final TariffService tariffService;

    public CatalogController(TariffService tariffService) {
        this.tariffService = tariffService;
    }

    // Her kimlik dogrulanmis kullanici tarifeleri okuyabilir (Redis cache'li).
    @GetMapping("/{code}")
    public ApiResponse<TariffResponse> getByCode(@PathVariable String code) {
        return ApiResponse.ok(tariffService.getByCode(code));
    }

    @GetMapping
    public ApiResponse<RestPage<TariffResponse>> list(Pageable pageable) {
        return ApiResponse.ok(tariffService.list(pageable));
    }

    // Yalnizca CATALOG_ADMIN tarife olusturabilir.
    @PostMapping
    @PreAuthorize("hasRole('CATALOG_ADMIN')")
    public ApiResponse<TariffResponse> create(@Valid @RequestBody CreateTariffRequest request) {
        return ApiResponse.ok(tariffService.create(request), "Tarife olusturuldu");
    }
}
