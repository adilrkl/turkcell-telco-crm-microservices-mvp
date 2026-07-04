package com.turkcell.ticketservice.application.features.ticket.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/** Talep acilis otomasyonu (G7, FR-32): oncelik -> SLA vadesi ve kategori -> ekip yonlendirmesi. */
class TicketOpeningPolicyTest {

    private final TicketOpeningPolicy policy = new TicketOpeningPolicy();
    private final Instant opened = Instant.parse("2026-07-04T10:00:00Z");

    @Test
    void slaDueAtScalesWithPriority() {
        assertThat(policy.slaDueAt("URGENT", opened)).isEqualTo(opened.plus(Duration.ofHours(4)));
        assertThat(policy.slaDueAt("HIGH", opened)).isEqualTo(opened.plus(Duration.ofHours(8)));
        assertThat(policy.slaDueAt("MEDIUM", opened)).isEqualTo(opened.plus(Duration.ofHours(24)));
        assertThat(policy.slaDueAt("LOW", opened)).isEqualTo(opened.plus(Duration.ofHours(72)));
    }

    @Test
    void slaDueAtIsCaseInsensitiveAndDefaultsForUnknownOrNull() {
        assertThat(policy.slaDueAt("urgent", opened)).isEqualTo(opened.plus(Duration.ofHours(4)));
        // bilinmeyen ve null -> varsayilan 24s
        assertThat(policy.slaDueAt("WHATEVER", opened)).isEqualTo(opened.plus(Duration.ofHours(24)));
        assertThat(policy.slaDueAt(null, opened)).isEqualTo(opened.plus(Duration.ofHours(24)));
    }

    @Test
    void routeTeamMapsKnownCategoriesAndFallsBackToGeneral() {
        assertThat(policy.routeTeam("BILLING")).isEqualTo("BILLING_TEAM");
        assertThat(policy.routeTeam("payment")).isEqualTo("BILLING_TEAM");
        assertThat(policy.routeTeam("TECHNICAL")).isEqualTo("TECH_TEAM");
        assertThat(policy.routeTeam("network")).isEqualTo("TECH_TEAM");
        assertThat(policy.routeTeam("SALES")).isEqualTo("SALES_TEAM");
        assertThat(policy.routeTeam("bilinmeyen-kategori")).isEqualTo("GENERAL_TEAM");
        assertThat(policy.routeTeam(null)).isEqualTo("GENERAL_TEAM");
    }
}
