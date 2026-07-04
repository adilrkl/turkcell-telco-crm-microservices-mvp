package com.turkcell.usageservice.application.features.usage.command.record;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.cqrs.CommandHandler;
import com.turkcell.usageservice.application.features.usage.mapper.UsageRecordMapper;
import com.turkcell.usageservice.dto.UsageRecordResponse;
import com.turkcell.usageservice.entity.UsageRecord;
import com.turkcell.usageservice.repository.UsageRecordRepository;
import com.turkcell.usageservice.service.QuotaService;

/** Senkron kullanim girisi: Kafka yoluyla ayni davranis (kayit + kota dusumu tek transaction). */
@Component
public class RecordUsageCommandHandler implements CommandHandler<RecordUsageCommand, UsageRecordResponse> {

    private final UsageRecordRepository repository;
    private final UsageRecordMapper mapper;
    private final QuotaService quotaService;

    public RecordUsageCommandHandler(UsageRecordRepository repository, UsageRecordMapper mapper,
                                     QuotaService quotaService) {
        this.repository = repository;
        this.mapper = mapper;
        this.quotaService = quotaService;
    }

    @Override
    @Transactional
    public UsageRecordResponse handle(RecordUsageCommand command) {
        UsageRecord saved = repository.save(mapper.toUsageRecord(command));
        quotaService.applyUsage(saved.getSubscriptionId(), saved.getType(),
                saved.getQuantity(), saved.getRecordedAt());
        return mapper.toResponse(saved);
    }
}
