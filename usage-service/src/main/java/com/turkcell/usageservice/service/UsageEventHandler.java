package com.turkcell.usageservice.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.usageservice.application.features.usage.mapper.UsageRecordMapper;
import com.turkcell.usageservice.entity.ProcessedEvent;
import com.turkcell.usageservice.entity.UsageRecord;
import com.turkcell.usageservice.event.UsageRecordedEvent;
import com.turkcell.usageservice.repository.ProcessedEventRepository;
import com.turkcell.usageservice.repository.UsageRecordRepository;

/**
 * UsageRecorded isleyicisi. Inbox kontrolu + usage_records yazimi + kota dusumu
 * AYNI transaction'da (idempotent: ayni eventId tekrar gelirse atlanir).
 */
@Service
public class UsageEventHandler {

    private static final Logger log = LoggerFactory.getLogger(UsageEventHandler.class);

    private final ProcessedEventRepository processedEventRepository;
    private final UsageRecordRepository usageRecordRepository;
    private final UsageRecordMapper mapper;
    private final QuotaService quotaService;

    public UsageEventHandler(ProcessedEventRepository processedEventRepository,
                             UsageRecordRepository usageRecordRepository,
                             UsageRecordMapper mapper,
                             QuotaService quotaService) {
        this.processedEventRepository = processedEventRepository;
        this.usageRecordRepository = usageRecordRepository;
        this.mapper = mapper;
        this.quotaService = quotaService;
    }

    @Transactional
    public void handle(UsageRecordedEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("usage: event zaten islendi, atlaniyor. eventId={}", event.eventId());
            return;
        }

        UsageRecord record = usageRecordRepository.save(mapper.fromEvent(event));
        quotaService.applyUsage(record.getSubscriptionId(), record.getType(),
                record.getQuantity(), record.getRecordedAt());

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(event.eventId());
        processed.setProcessedAt(Instant.now());
        processedEventRepository.save(processed);

        log.info("usage: UsageRecorded islendi. sub={} type={} qty={}",
                event.subscriptionId(), event.type(), event.quantity());
    }
}
