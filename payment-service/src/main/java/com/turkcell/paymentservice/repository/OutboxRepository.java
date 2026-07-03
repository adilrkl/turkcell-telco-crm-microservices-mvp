package com.turkcell.paymentservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.turkcell.paymentservice.entity.OutboxEvent;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * PENDING satirlari kilitleyerek alir (FOR UPDATE SKIP LOCKED): birden fazla
     * poller instance'i ayni anda calisirsa herkes FARKLI satirlari isler,
     * ayni event iki kez publish edilmez. Kilit, poller'in transaction'i bitince birakilir.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING' AND retry_count < 3
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findPublishable(@Param("limit") int limit);
}
