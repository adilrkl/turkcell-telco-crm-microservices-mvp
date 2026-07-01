package com.turkcell.commonlib.cqrs;

/**
 * Command/Query'leri ilgili handler'a yonlendiren mediator (MediatR benzeri).
 * Controller'lar handler'lara dogrudan bagimli olmak yerine sadece bu arayuze bagimlidir.
 */
public interface Mediator {

    <R> R send(Command<R> command);

    <R> R send(Query<R> query);
}
