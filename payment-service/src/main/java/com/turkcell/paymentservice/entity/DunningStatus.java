package com.turkcell.paymentservice.entity;

/** dunning_schedules.status degerleri (G8, FR-27). */
public final class DunningStatus {

    private DunningStatus() {
    }

    /** Retry bekliyor (vadesi gelince scheduler dener). */
    public static final String PENDING = "PENDING";
    /** Bir retry basarili oldu (fatura tahsil edildi). */
    public static final String RESOLVED = "RESOLVED";
    /** Tum retry'lar tukendi, tahsilat alinamadi. */
    public static final String EXHAUSTED = "EXHAUSTED";
}
