package com.turkcell.commonlib.cqrs;

/**
 * Belirli bir {@link Query} tipini isleyen handler.
 *
 * @param <Q> islenecek query tipi
 * @param <R> query'nin donus tipi
 */
public interface QueryHandler<Q extends Query<R>, R> {
    R handle(Q query);
}
