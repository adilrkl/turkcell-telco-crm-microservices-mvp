package com.turkcell.customerservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.turkcell.customerservice.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByCustomerId(UUID customerId);

    boolean existsByCustomerId(UUID customerId);
}
