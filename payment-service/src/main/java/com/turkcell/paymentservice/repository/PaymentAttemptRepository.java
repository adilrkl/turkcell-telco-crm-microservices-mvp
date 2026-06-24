package com.turkcell.paymentservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.turkcell.paymentservice.entity.PaymentAttempt;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    long countByPaymentId(UUID paymentId);
}
