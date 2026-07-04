package com.turkcell.usageservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.turkcell.usageservice.entity.SubscriptionEntitlement;

public interface SubscriptionEntitlementRepository extends JpaRepository<SubscriptionEntitlement, UUID> {
}
