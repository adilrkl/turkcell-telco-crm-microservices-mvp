package com.turkcell.gatewayserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Context-load testi. RateLimitConfig acilista GERCEK Redis baglantisi kurar;
 * compose altyapisina bagimli kalmamak icin (CI!) Redis Testcontainers ile ayaga
 * kaldirilir ve telco.ratelimit.redis.* container'a yonlendirilir.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@Testcontainers(disabledWithoutDocker = true)
class GatewayServerApplicationTests {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void rateLimitRedis(DynamicPropertyRegistry registry) {
        registry.add("telco.ratelimit.redis.host", redis::getHost);
        registry.add("telco.ratelimit.redis.port", redis::getFirstMappedPort);
    }

    @Test
    void contextLoads() {
    }
}
