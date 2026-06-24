package com.turkcell.gatewayserver.ratelimit;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gateway rate-limit ayarlari (config-server: gateway-server.yaml -> telco.ratelimit.*).
 *
 * <p>docx §13: "Gateway'de Redis tabanli; user basina 100 req/min varsayilan."
 * Limit token-bucket olarak Redis'te (Lettuce + Bucket4j CAS) tutulur; boylece
 * gateway yatay olceklendiginde (docx §5 stateless/HPA) tum instance'lar ayni
 * sayaci paylasir.</p>
 */
@ConfigurationProperties(prefix = "telco.ratelimit")
public class RateLimitProperties {

    /** Rate-limit'i tamamen acar/kapatir. */
    private boolean enabled = true;

    /** Pencere basina izin verilen istek sayisi (bucket kapasitesi). docx: 100. */
    private long capacity = 100;

    /** Yenilenme penceresi. docx: 1 dakika. */
    private Duration period = Duration.ofMinutes(1);

    /** Rate-limit DISI birakilan yol on-ekleri (actuator/health scrape vb. limitlenmez). */
    private List<String> excludedPaths = List.of("/actuator");

    /** Redis baglanti ayarlari (host'ta calisan gateway -> localhost:6379). */
    private final Redis redis = new Redis();

    public static class Redis {
        private String host = "localhost";
        private int port = 6379;
        /** Bos ise auth yok. */
        private String password = "";

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getCapacity() { return capacity; }
    public void setCapacity(long capacity) { this.capacity = capacity; }
    public Duration getPeriod() { return period; }
    public void setPeriod(Duration period) { this.period = period; }
    public List<String> getExcludedPaths() { return excludedPaths; }
    public void setExcludedPaths(List<String> excludedPaths) { this.excludedPaths = excludedPaths; }
    public Redis getRedis() { return redis; }
}
