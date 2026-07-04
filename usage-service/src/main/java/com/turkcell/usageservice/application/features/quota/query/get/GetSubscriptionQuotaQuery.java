package com.turkcell.usageservice.application.features.quota.query.get;

import java.util.UUID;

import com.turkcell.commonlib.cqrs.Query;
import com.turkcell.usageservice.dto.QuotaResponse;

/** Bir aboneligin ICINDE BULUNULAN donem (takvim ayi) kalan kotasi. */
public record GetSubscriptionQuotaQuery(UUID subscriptionId) implements Query<QuotaResponse> {
}
