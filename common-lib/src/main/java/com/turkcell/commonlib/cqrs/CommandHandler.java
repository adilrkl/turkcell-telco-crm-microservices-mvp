package com.turkcell.commonlib.cqrs;

/**
 * Belirli bir {@link Command} tipini isleyen handler.
 *
 * @param <C> islenecek command tipi
 * @param <R> command'in donus tipi
 */
public interface CommandHandler<C extends Command<R>, R> {
    R handle(C command);
}
