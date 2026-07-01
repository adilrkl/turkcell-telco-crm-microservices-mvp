package com.turkcell.commonlib.cqrs.pipeline;

/**
 * Pipeline zincirinde bir sonraki adimi (behavior ya da handler'in kendisi) temsil eden delegate.
 *
 * @param <R> donus tipi
 */
@FunctionalInterface
public interface RequestHandlerDelegate<R> {
    R invoke();
}
