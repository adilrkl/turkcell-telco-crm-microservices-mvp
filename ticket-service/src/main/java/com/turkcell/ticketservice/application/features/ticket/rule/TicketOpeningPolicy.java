package com.turkcell.ticketservice.application.features.ticket.rule;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Talep acilis otomasyonu (G7, FR-32): oncelige gore SLA vade hesabi + kategoriye gore ekip
 * yonlendirmesi (basit auto-assign). Degerler MVP icin kod-icinde sabit; ileride config'e alinabilir.
 *
 * <p>Ekip atamasi "ilgili ekibe yonlendirme"dir (kuyruk); bireysel CSR atamasi mevcut
 * {@code assigned_to} + assign endpoint'iyle elle yapilmaya devam eder.
 */
@Component
public class TicketOpeningPolicy {

    /** Oncelik -> ilk yanit SLA suresi (talep acilisindan itibaren). */
    private static final Map<String, Duration> SLA_BY_PRIORITY = Map.of(
            "URGENT", Duration.ofHours(4),
            "HIGH",   Duration.ofHours(8),
            "MEDIUM", Duration.ofHours(24),
            "LOW",    Duration.ofHours(72));

    /** Bilinmeyen/null oncelik icin varsayilan SLA. */
    private static final Duration DEFAULT_SLA = Duration.ofHours(24);

    /** Kategori (buyuk harfe normalize) -> yonlendirilecek ekip. */
    private static final Map<String, String> TEAM_BY_CATEGORY = Map.of(
            "BILLING",   "BILLING_TEAM",
            "PAYMENT",   "BILLING_TEAM",
            "TECHNICAL", "TECH_TEAM",
            "NETWORK",   "TECH_TEAM",
            "SALES",     "SALES_TEAM");

    /** Eslesmeyen kategoriler genel kuyruga duser. */
    private static final String DEFAULT_TEAM = "GENERAL_TEAM";

    /** Talep acilisindan itibaren SLA vade zamani. */
    public Instant slaDueAt(String priority, Instant openedAt) {
        Duration sla = priority == null ? DEFAULT_SLA
                : SLA_BY_PRIORITY.getOrDefault(priority.toUpperCase(), DEFAULT_SLA);
        return openedAt.plus(sla);
    }

    /** Kategoriye gore yonlendirilecek ekip; eslesme yoksa genel kuyruk. */
    public String routeTeam(String category) {
        if (category == null) {
            return DEFAULT_TEAM;
        }
        return TEAM_BY_CATEGORY.getOrDefault(category.toUpperCase(), DEFAULT_TEAM);
    }
}
