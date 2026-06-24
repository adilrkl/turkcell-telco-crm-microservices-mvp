package com.turkcell.subscriptionservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.turkcell.subscriptionservice.entity.MsisdnPool;

public interface MsisdnPoolRepository extends JpaRepository<MsisdnPool, String> {

    /** Havuzdan bos (FREE) ilk numarayi getir (rezervasyon icin). */
    Optional<MsisdnPool> findFirstByStatusOrderByMsisdn(String status);
}
