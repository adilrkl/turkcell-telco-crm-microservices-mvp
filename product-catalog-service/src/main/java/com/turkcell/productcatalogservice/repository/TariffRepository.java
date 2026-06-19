package com.turkcell.productcatalogservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.turkcell.productcatalogservice.entity.Tariff;

public interface TariffRepository extends JpaRepository<Tariff, UUID> {
    Optional<Tariff> findByCode(String code);
    boolean existsByCode(String code);
}
