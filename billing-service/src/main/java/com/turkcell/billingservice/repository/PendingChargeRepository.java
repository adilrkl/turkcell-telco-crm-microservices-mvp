package com.turkcell.billingservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.turkcell.billingservice.entity.PendingCharge;

public interface PendingChargeRepository extends JpaRepository<PendingCharge, UUID> {

    /**
     * Bir aboneligin bekleyen asim ucretlerini kilitleyerek alir (FOR UPDATE):
     * bill-run bunlari faturaya eklerken es zamanli ikinci bir isleme kapatilir.
     * Kilit, bill-run transaction'i bitince birakilir.
     */
    @Query(value = """
            SELECT * FROM pending_charges
            WHERE subscription_id = :subscriptionId AND status = 'PENDING'
            ORDER BY created_at
            FOR UPDATE
            """, nativeQuery = true)
    List<PendingCharge> findPendingForUpdate(@Param("subscriptionId") UUID subscriptionId);
}
