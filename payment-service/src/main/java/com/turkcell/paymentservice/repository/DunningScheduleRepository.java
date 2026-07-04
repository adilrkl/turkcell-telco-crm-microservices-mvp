package com.turkcell.paymentservice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.turkcell.paymentservice.entity.DunningSchedule;

public interface DunningScheduleRepository extends JpaRepository<DunningSchedule, UUID> {

    Optional<DunningSchedule> findByInvoiceId(UUID invoiceId);

    /**
     * Vadesi gelmis PENDING planlari kilitleyerek alir (FOR UPDATE SKIP LOCKED): birden fazla
     * instance ayni anda calisirsa her biri FARKLI planlari isler, ayni fatura iki kez re-charge
     * edilmez. Kilit, scheduler'in transaction'i bitince birakilir.
     */
    @Query(value = """
            SELECT * FROM dunning_schedules
            WHERE status = 'PENDING' AND next_retry_at <= :now
            ORDER BY next_retry_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<DunningSchedule> findDue(@Param("now") Instant now, @Param("limit") int limit);
}
