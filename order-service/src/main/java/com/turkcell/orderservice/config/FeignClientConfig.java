package com.turkcell.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import feign.RequestInterceptor;
import feign.Retryer;

/**
 * Gelen istegin Authorization (Bearer) header'ini Feign cagrilarina tasir.
 * Aksi halde downstream resource-server'lar 401 doner.
 */
@Configuration
public class FeignClientConfig {

    @Bean
    public RequestInterceptor bearerTokenRelayInterceptor() {
        return template -> {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                String authorization = attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
                if (authorization != null && !authorization.isBlank()) {
                    template.header(HttpHeaders.AUTHORIZATION, authorization);
                }
            }
        };
    }

    /**
     * Ag-seviyesi hatalarda (connect/read IOException -> RetryableException) 1 kez
     * yeniden dener; HTTP cevabi donen hatalar retry EDILMEZ. Iki cagri da idempotent
     * GET oldugundan guvenlidir. Retry, circuit breaker'in ICINDE calisir: breaker
     * yalnizca nihai sonucu sayar. (Spring Cloud OpenFeign varsayilani NEVER_RETRY.)
     */
    @Bean
    public Retryer feignRetryer() {
        return new Retryer.Default(100, 500, 2);
    }
}
