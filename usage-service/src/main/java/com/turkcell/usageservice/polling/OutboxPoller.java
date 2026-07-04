package com.turkcell.usageservice.polling;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeTypeUtils;

import com.turkcell.commonlib.saga.SagaHeaders;
import com.turkcell.usageservice.entity.OutboxEvent;
import com.turkcell.usageservice.entity.OutboxStatus;
import com.turkcell.usageservice.repository.OutboxRepository;

/** Transactional Outbox relay: PENDING satirlari (kota event'leri) quota-events'e publish eder. */
@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int MAX_RETRY = 3;

    private final OutboxRepository outboxRepository;
    private final StreamBridge streamBridge;

    public OutboxPoller(OutboxRepository outboxRepository, StreamBridge streamBridge) {
        this.outboxRepository = outboxRepository;
        this.streamBridge = streamBridge;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxRepository.findPublishable(100);
        for (OutboxEvent event : events) {
            try {
                boolean sent = streamBridge.send(event.getDestination(),
                        MessageBuilder.withPayload(event.getPayload().getBytes(StandardCharsets.UTF_8))
                                .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON_VALUE)
                                .setHeader(SagaHeaders.EVENT_TYPE, event.getEventType())
                                .build());
                if (!sent) {
                    throw new IllegalStateException("StreamBridge.send false dondu");
                }
                event.setStatus(OutboxStatus.SENT);
                event.setPublishedAt(Instant.now());
                log.info("Outbox SENT -> {}: type={} id={}", event.getDestination(), event.getEventType(), event.getId());
            } catch (Exception e) {
                if (event.getRetryCount() + 1 >= MAX_RETRY) {
                    event.setStatus(OutboxStatus.FAILED);
                } else {
                    event.setRetryCount(event.getRetryCount() + 1);
                }
                event.setErrorMessage(e.getMessage());
                log.warn("Outbox publish hatasi (id={}): {}", event.getId(), e.getMessage());
            }
            event.setProcessedAt(Instant.now());
            outboxRepository.save(event);
        }
    }
}
