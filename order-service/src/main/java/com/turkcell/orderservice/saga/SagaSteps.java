package com.turkcell.orderservice.saga;

import java.util.List;

/** Saga adim adlari (saga_states.current_step). Order ilerledikçe bu degerleri alir. */
public final class SagaSteps {

    private SagaSteps() {
    }

    public static final String STARTED = "STARTED";
    public static final String MSISDN_RESERVED = "MSISDN_RESERVED";
    public static final String PAYMENT_COMPLETED = "PAYMENT_COMPLETED";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    /** Terminal adimlar - timeout taramasi bunlari atlar. */
    public static final List<String> TERMINAL = List.of(COMPLETED, CANCELLED);
}
