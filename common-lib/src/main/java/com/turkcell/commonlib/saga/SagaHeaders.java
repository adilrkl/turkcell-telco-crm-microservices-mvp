package com.turkcell.commonlib.saga;

/**
 * Saga mesajlarinda kullanilan Kafka header anahtarlari.
 * Tum saga komut/reply'lari TEK topic uzerinden ham JSON byte olarak akar;
 * tuketici {@link #EVENT_TYPE} header'ina bakarak dogru tipe dispatch eder.
 */
public final class SagaHeaders {

    private SagaHeaders() {
    }

    /** Payload'in event tipi. Degeri ilgili event record'unun simple class adidir (orn. "PaymentCompleted"). */
    public static final String EVENT_TYPE = "eventType";
}
