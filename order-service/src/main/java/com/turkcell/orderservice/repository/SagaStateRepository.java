package com.turkcell.orderservice.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.turkcell.orderservice.entity.SagaState;

public interface SagaStateRepository extends JpaRepository<SagaState, UUID> {

    Optional<SagaState> findByOrderId(UUID orderId);

    /** Timeout taramasi: terminal olmayan (COMPLETED/CANCELLED disi) ve belirtilen andan once guncellenmis saga'lar. */
    List<SagaState> findByCurrentStepNotInAndLastUpdatedBefore(Collection<String> steps, Instant before);
}
