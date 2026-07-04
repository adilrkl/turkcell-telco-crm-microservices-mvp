package com.turkcell.paymentservice.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dunning retry ayarlari (config-server: payment-service.yaml -> payment.dunning.*).
 *
 * <p>docx FR-27: "Basarisiz odemelerde 24/72/168 saat aralikla retry tetiklenir."
 * intervals origin (ilk basarisizlik) anindan itibaren offset'lerdir: retry N,
 * origin_failed_at + intervals[N] zamaninda denenir. Demo icin sureler kisaltilabilir
 * (orn. 10s,20s,30s) ve {@code demoRecoverOnRetry} ile transient toparlanma simule edilir.</p>
 */
@ConfigurationProperties(prefix = "payment.dunning")
public class DunningProperties {

    /** Retry offset'leri (origin_failed_at'a gore). Adet = maksimum retry sayisi. */
    private List<Duration> intervals = List.of(
            Duration.ofHours(24), Duration.ofHours(72), Duration.ofHours(168));

    /**
     * Demo aracı: true ise dunning retry'lari tutardan bagimsiz ONAYLANIR (gecici hatanin
     * gectigini simule eder → recovery gorulur). false (varsayilan) → gercek PSP kurali
     * (tutar esigi) uygulanir, 1000 TRY ustu faturalar tum retry'lardan sonra EXHAUSTED olur.
     */
    private boolean demoRecoverOnRetry = false;

    public List<Duration> getIntervals() { return intervals; }
    public void setIntervals(List<Duration> intervals) { this.intervals = intervals; }
    public boolean isDemoRecoverOnRetry() { return demoRecoverOnRetry; }
    public void setDemoRecoverOnRetry(boolean demoRecoverOnRetry) { this.demoRecoverOnRetry = demoRecoverOnRetry; }
}
