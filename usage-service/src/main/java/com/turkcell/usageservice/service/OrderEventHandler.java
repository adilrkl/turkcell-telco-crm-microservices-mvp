package com.turkcell.usageservice.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.saga.OrderConfirmed;
import com.turkcell.usageservice.entity.ProcessedEvent;
import com.turkcell.usageservice.repository.ProcessedEventRepository;

/**
 * OrderConfirmed isleyicisi (saga DISI reaksiyon): tarife hak fotografini yazar,
 * icinde bulunulan donemin kotasini acar. Inbox kontrolu + yazimlar AYNI transaction'da.
 */
@Service
public class OrderEventHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderEventHandler.class);

    private final ProcessedEventRepository processedEventRepository;
    private final QuotaService quotaService;

    public OrderEventHandler(ProcessedEventRepository processedEventRepository,
                             QuotaService quotaService) {
        this.processedEventRepository = processedEventRepository;
        this.quotaService = quotaService;
    }

    @Transactional
    public void handleConfirmed(OrderConfirmed event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("usage: event zaten islendi, atlaniyor. eventId={}", event.eventId());
            return;
        }

        quotaService.provision(event);

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(event.eventId());
        processed.setProcessedAt(Instant.now());
        processedEventRepository.save(processed);
    }
}
