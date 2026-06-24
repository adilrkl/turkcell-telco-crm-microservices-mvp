package com.turkcell.orderservice.saga;

/** orders.status degerleri (MVP dokumani FR-11). */
public final class OrderStatus {

    private OrderStatus() {
    }

    public static final String DRAFT = "DRAFT";
    public static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String PAID = "PAID";
    public static final String FULFILLED = "FULFILLED";
    public static final String CANCELLED = "CANCELLED";
}
