package com.turkcell.usageservice.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.turkcell.usageservice.entity.Quota;

public interface QuotaRepository extends JpaRepository<Quota, UUID> {

    Optional<Quota> findBySubscriptionIdAndPeriodStart(UUID subscriptionId, LocalDate periodStart);

    /**
     * Kota satirini kilitleyerek alir (FOR UPDATE): ayni abonelige es zamanli iki
     * kullanim event'i gelirse dusum kaybolmaz, esik event'i cift uretilmez.
     * Kilit, cagiranin transaction'i bitince birakilir.
     */
    @Query(value = """
            SELECT * FROM quotas
            WHERE subscription_id = :subscriptionId AND period_start = :periodStart
            FOR UPDATE
            """, nativeQuery = true)
    Optional<Quota> findForUpdate(@Param("subscriptionId") UUID subscriptionId,
                                  @Param("periodStart") LocalDate periodStart);
}
