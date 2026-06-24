package com.turkcell.subscriptionservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.turkcell.subscriptionservice.entity.SimCard;

public interface SimCardRepository extends JpaRepository<SimCard, String> {
}
