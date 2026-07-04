package com.turkcell.usageservice.controller;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.turkcell.commonlib.cache.RestPage;
import com.turkcell.commonlib.cqrs.Mediator;
import com.turkcell.commonlib.dto.ApiResponse;
import com.turkcell.usageservice.application.features.quota.query.get.GetSubscriptionQuotaQuery;
import com.turkcell.usageservice.application.features.usage.command.record.RecordUsageCommand;
import com.turkcell.usageservice.application.features.usage.query.list.ListUsageRecordsQuery;
import com.turkcell.usageservice.application.features.usage.query.summary.GetSubscriptionUsageSummaryQuery;
import com.turkcell.usageservice.dto.QuotaResponse;
import com.turkcell.usageservice.dto.RecordUsageRequest;
import com.turkcell.usageservice.dto.UsageRecordResponse;
import com.turkcell.usageservice.dto.UsageSummaryResponse;

import jakarta.validation.Valid;

/** Kullanim/CDR: senkron giris (ADMIN) + abonelik bazli okuma/agregasyon (CSR/ADMIN). */
@RestController
@RequestMapping("/api/usage")
public class UsageController {

    private final Mediator mediator;

    public UsageController(Mediator mediator) {
        this.mediator = mediator;
    }

    /** Tekil kullanim kaydi girisi (teknik/ADMIN). Kafka usage-events akisiyla ayni yazim yolu. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UsageRecordResponse> record(@Valid @RequestBody RecordUsageRequest request) {
        RecordUsageCommand command = new RecordUsageCommand(
                request.subscriptionId(), request.type(), request.quantity(),
                request.recordedAt(), request.cdrRef());
        return ApiResponse.ok(mediator.send(command), "Kullanim kaydedildi");
    }

    /** Bir abonelige ait sayfali ham kullanim kayitlari (CSR/ADMIN). */
    @GetMapping("/records")
    @PreAuthorize("hasAnyRole('CSR','ADMIN')")
    public ApiResponse<RestPage<UsageRecordResponse>> listRecords(@RequestParam UUID subscriptionId,
                                                                  Pageable pageable) {
        return ApiResponse.ok(mediator.send(new ListUsageRecordsQuery(subscriptionId, pageable)));
    }

    /** Bir aboneligin icinde bulunulan donem (takvim ayi) kalan kotasi (CSR/ADMIN). */
    @GetMapping("/quota")
    @PreAuthorize("hasAnyRole('CSR','ADMIN')")
    public ApiResponse<QuotaResponse> quota(@RequestParam UUID subscriptionId) {
        return ApiResponse.ok(mediator.send(new GetSubscriptionQuotaQuery(subscriptionId)));
    }

    /** Bir abonelik icin [from, to) donem kullanim ozeti (CSR/ADMIN). Tarihler ISO-8601 (orn. 2026-07-01T00:00:00Z). */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('CSR','ADMIN')")
    public ApiResponse<UsageSummaryResponse> summary(
            @RequestParam UUID subscriptionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponse.ok(mediator.send(new GetSubscriptionUsageSummaryQuery(subscriptionId, from, to)));
    }
}
