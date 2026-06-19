package com.turkcell.billingservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.turkcell.billingservice.entity.BillCycle;

public interface BillCycleRepository extends JpaRepository<BillCycle, UUID> {
}
