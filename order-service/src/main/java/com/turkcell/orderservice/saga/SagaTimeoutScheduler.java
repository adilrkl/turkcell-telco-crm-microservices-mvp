package com.turkcell.orderservice.saga;

import java.time.Duration;
import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cevap gelmeyen saga'lari periyodik tarar ve iptal/compensation tetikler.
 * Bir participant hic reply uretmezse orchestrator sonsuza kadar beklemesin diye.
 */
@Component
public class SagaTimeoutScheduler {

    private static final Duration TIMEOUT = Duration.ofMinutes(2);

    private final OrderSagaOrchestrator orchestrator;

    public SagaTimeoutScheduler(OrderSagaOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(fixedDelay = 30_000)
    public void sweep() {
        orchestrator.sweepTimeouts(Instant.now().minus(TIMEOUT));
    }
}
