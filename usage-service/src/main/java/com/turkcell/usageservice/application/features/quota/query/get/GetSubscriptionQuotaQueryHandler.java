package com.turkcell.usageservice.application.features.quota.query.get;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.cqrs.QueryHandler;
import com.turkcell.commonlib.exception.ResourceNotFoundException;
import com.turkcell.usageservice.dto.QuotaItem;
import com.turkcell.usageservice.dto.QuotaResponse;
import com.turkcell.usageservice.entity.Quota;
import com.turkcell.usageservice.entity.SubscriptionEntitlement;
import com.turkcell.usageservice.repository.QuotaRepository;
import com.turkcell.usageservice.repository.SubscriptionEntitlementRepository;
import com.turkcell.usageservice.service.QuotaService;

/**
 * Icinde bulunulan donemin kota satirini okur. Donemde henuz kullanim yoksa
 * (satir lazy acilmamis) hak fotografindan tam-dolu bir goruntu SENTEZLENIR,
 * yazma yapilmaz (read-only). Hak fotografi da yoksa 404.
 */
@Component
public class GetSubscriptionQuotaQueryHandler
        implements QueryHandler<GetSubscriptionQuotaQuery, QuotaResponse> {

    private final QuotaRepository quotaRepository;
    private final SubscriptionEntitlementRepository entitlementRepository;

    public GetSubscriptionQuotaQueryHandler(QuotaRepository quotaRepository,
                                            SubscriptionEntitlementRepository entitlementRepository) {
        this.quotaRepository = quotaRepository;
        this.entitlementRepository = entitlementRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public QuotaResponse handle(GetSubscriptionQuotaQuery query) {
        LocalDate periodStart = QuotaService.periodStartOf(Instant.now());

        return quotaRepository.findBySubscriptionIdAndPeriodStart(query.subscriptionId(), periodStart)
                .map(GetSubscriptionQuotaQueryHandler::fromQuota)
                .orElseGet(() -> entitlementRepository.findById(query.subscriptionId())
                        .map(ent -> fromEntitlement(ent, periodStart))
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Kota (abonelik icin tarife hak kaydi yok)", query.subscriptionId())));
    }

    private static QuotaResponse fromQuota(Quota q) {
        List<QuotaItem> items = new ArrayList<>();
        addItem(items, "VOICE", q.getMinutesTotal(), q.getMinutesRemaining());
        addItem(items, "SMS", q.getSmsTotal(), q.getSmsRemaining());
        addItem(items, "DATA", q.getMbTotal(), q.getMbRemaining());
        return new QuotaResponse(q.getSubscriptionId(), q.getPeriodStart(), q.getPeriodEnd(), items);
    }

    private static QuotaResponse fromEntitlement(SubscriptionEntitlement ent, LocalDate periodStart) {
        List<QuotaItem> items = new ArrayList<>();
        addItem(items, "VOICE", toDecimal(ent.getMinutesIncluded()), toDecimal(ent.getMinutesIncluded()));
        addItem(items, "SMS", toDecimal(ent.getSmsIncluded()), toDecimal(ent.getSmsIncluded()));
        addItem(items, "DATA", toDecimal(ent.getMbIncluded()), toDecimal(ent.getMbIncluded()));
        return new QuotaResponse(ent.getSubscriptionId(), periodStart, periodStart.plusMonths(1), items);
    }

    private static void addItem(List<QuotaItem> items, String type, BigDecimal total, BigDecimal remaining) {
        if (total == null || total.signum() <= 0) {
            return; // bu tip icin hak tanimsiz -> kota kartinda gosterilmez
        }
        BigDecimal rem = remaining != null ? remaining : BigDecimal.ZERO;
        int usedPct = total.subtract(rem)
                .multiply(new BigDecimal(100))
                .divide(total, 0, RoundingMode.HALF_UP)
                .intValue();
        items.add(new QuotaItem(type, total, rem, usedPct));
    }

    private static BigDecimal toDecimal(Integer value) {
        return value != null ? new BigDecimal(value) : null;
    }
}
