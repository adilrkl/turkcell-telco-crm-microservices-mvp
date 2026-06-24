package com.turkcell.gatewayserver.ratelimit;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Edge rate-limit filtresi (Gateway Server WebMVC, servlet stack).
 *
 * <p>Her istegi yonlendirmeden ONCE keser: anahtar basina (user/IP) Redis'teki
 * token-bucket'tan 1 token tuketmeye calisir. Token varsa gecirir, yoksa
 * {@code 429 Too Many Requests} + {@code Retry-After} dondurur. Limit/kalan bilgisi
 * {@code X-RateLimit-*} header'lariyla her yanitta verilir.</p>
 *
 * <p>{@code /actuator/**} gibi haric yollar (Prometheus scrape, health) limitlenmez.</p>
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final LettuceBasedProxyManager<String> proxyManager;
    private final RateLimitKeyResolver keyResolver;
    private final RateLimitProperties props;
    private final ObjectMapper objectMapper;
    private final BucketConfiguration bucketConfiguration;

    public RateLimitFilter(LettuceBasedProxyManager<String> proxyManager,
                           RateLimitKeyResolver keyResolver,
                           RateLimitProperties props,
                           ObjectMapper objectMapper) {
        this.proxyManager = proxyManager;
        this.keyResolver = keyResolver;
        this.props = props;
        this.objectMapper = objectMapper;
        // Tek pencere: capacity token / period; greedy refill (pencere boyunca esit dagilim).
        long cap = props.getCapacity();
        Duration period = props.getPeriod();
        this.bucketConfiguration = BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(cap).refillGreedy(cap, period))
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!props.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        for (String prefix : props.getExcludedPaths()) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = keyResolver.resolve(request);
        Bucket bucket = proxyManager.getProxy("telco:rl:" + key, () -> bucketConfiguration);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        response.setHeader("X-RateLimit-Limit", Long.toString(props.getCapacity()));
        response.setHeader("X-RateLimit-Remaining", Long.toString(Math.max(0, probe.getRemainingTokens())));

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = (long) Math.ceil(probe.getNanosToWaitForRefill() / 1_000_000_000.0);
        writeTooManyRequests(response, retryAfterSeconds);
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        response.setHeader("X-RateLimit-Retry-After-Seconds", Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        // Proje konvansiyonu: common-lib ApiResponse sekli (success/message/errorCode).
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", "Rate limit asildi. Lutfen " + retryAfterSeconds + " sn sonra tekrar deneyin.");
        body.put("errorCode", "RATE_LIMIT_EXCEEDED");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
