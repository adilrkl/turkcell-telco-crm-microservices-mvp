package com.turkcell.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.turkcell.orderservice.client.dto.TariffEnvelope;

/** product-catalog-service'e senkron cagri (fiyat cekme; cevap Redis cache'li). */
@FeignClient(name = "product-catalog-service")
public interface ProductCatalogClient {

    @GetMapping("/api/catalog/tariffs/{code}")
    TariffEnvelope getByCode(@PathVariable("code") String code);
}
