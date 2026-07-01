package com.turkcell.commonlib.cqrs.pipeline;

/**
 * Command/Query handler cagrilmadan once/sonra calisan capraz kesit (cross-cutting)
 * davranisi. Handler'lar zincir halinde ({@code @Order} ile siralanir) calistirilir.
 */
public interface PipelineBehavior {

    <R> R handle(Object request, RequestHandlerDelegate<R> next);

    /** Bu behavior verilen istek icin calissin mi? Varsayilan: her istek. */
    default boolean supports(Object request) {
        return true;
    }
}
