package com.turkcell.commonlib.cqrs.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

/**
 * Her command/query icin (istisna: {@link NotLoggableRequest}) mediator pipeline'inda
 * calisan basit loglama + sure olcumu behavior'i.
 */
@Order(20)
public class LoggingBehavior implements PipelineBehavior {

    private static final Logger log = LoggerFactory.getLogger(LoggingBehavior.class);

    @Override
    public boolean supports(Object request) {
        return !(request instanceof NotLoggableRequest);
    }

    @Override
    public <R> R handle(Object request, RequestHandlerDelegate<R> next) {
        String name = request.getClass().getSimpleName();
        long start = System.nanoTime();
        log.debug("CQRS handling {}", name);
        try {
            return next.invoke();
        } finally {
            log.debug("CQRS handled {} in {} ms", name, (System.nanoTime() - start) / 1_000_000);
        }
    }
}
