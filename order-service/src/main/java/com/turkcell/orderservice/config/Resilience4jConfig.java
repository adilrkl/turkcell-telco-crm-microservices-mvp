package com.turkcell.orderservice.config;

import java.time.Duration;

import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;

/**
 * Feign cagrilarindaki circuit breaker'larin varsayilan ayari.
 * Son 10 cagrinin >= %50'si hata ise devre 10 sn ACIK kalir (istekler aninda
 * fallback'e duser), sonra half-open'da 3 deneme ile toparlanir.
 * 4xx cevaplar is hatasidir, devreyi SAYMAZ. Kontrol status uzerinden yapilir
 * (instanceof FeignClientException degil): default ErrorDecoder, Retry-After
 * header'li cevaplari RetryableException'a cevirir ve o FeignClientException
 * DEGILDIR - 4xx muafiyeti o durumda da gecerli kalmali.
 * Not: disableThreadPool=true oldugundan cagri ayni thread'de kalir; timeout'u
 * Feign read-timeout saglar, TimeLimiter ayari yedek olarak durur.
 */
@Configuration
public class Resilience4jConfig {

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCircuitBreakerCustomizer() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .ignoreException(t -> t instanceof FeignException fe
                        && fe.status() >= 400 && fe.status() < 500)
                .build();

        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(5))
                .build();

        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(circuitBreakerConfig)
                .timeLimiterConfig(timeLimiterConfig)
                .build());
    }
}
