package com.turkcell.orderservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.turkcell.orderservice.entity.Order;

public interface OrderRepository extends JpaRepository<Order, UUID> {
}
