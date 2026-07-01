package com.turkcell.commonlib.cqrs.pipeline;

/**
 * Bu arayuz ile isaretlenen command/query'ler {@link LoggingBehavior} tarafindan loglanmaz.
 * Cogu istek loglanir; loglanmamasi gereken az sayidaki istek bu arayuzu implement eder.
 */
public interface NotLoggableRequest {
}
