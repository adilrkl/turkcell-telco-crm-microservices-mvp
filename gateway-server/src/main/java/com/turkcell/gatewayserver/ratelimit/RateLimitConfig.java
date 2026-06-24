package com.turkcell.gatewayserver.ratelimit;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;

/**
 * Rate-limit altyapisi: Lettuce ile Redis baglantisi ve Bucket4j proxy-manager.
 *
 * <p>Bucket4j sayaclari Redis'te String anahtarli, byte[] degerli saklar; eszamanli
 * erisim Compare&amp;Swap ile cozulur (LettuceBasedProxyManager CAS variant).
 * Bos kalan bucket'lar pencere suresi sonunda otomatik silinir (TTL) -> Redis
 * sismez.</p>
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    /** Rate-limit'e ozel ham Lettuce client (Spring cache RedisTemplate'inden bagimsiz). */
    @Bean(destroyMethod = "shutdown")
    public RedisClient rateLimitRedisClient(RateLimitProperties props) {
        RedisURI.Builder uri = RedisURI.builder()
                .withHost(props.getRedis().getHost())
                .withPort(props.getRedis().getPort());
        if (props.getRedis().getPassword() != null && !props.getRedis().getPassword().isBlank()) {
            uri.withPassword(props.getRedis().getPassword().toCharArray());
        }
        return RedisClient.create(uri.build());
    }

    /** Bucket4j String-anahtar / byte[]-deger codec ister. */
    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, byte[]> rateLimitRedisConnection(RedisClient client) {
        RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
        return client.connect(codec);
    }

    @Bean
    public LettuceBasedProxyManager<String> bucketProxyManager(
            StatefulRedisConnection<String, byte[]> connection, RateLimitProperties props) {
        // Idle bucket TTL'i pencere suresi kadar; refill tamamlandiginda key dusebilir.
        Duration ttl = props.getPeriod();
        return Bucket4jLettuce.casBasedBuilder(connection)
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(ttl))
                .build();
    }

    /**
     * Filtreyi yonlendirmeden ONCE calistirmak icin yuksek oncelikle kaydeder.
     * Plain class olarak (varsayilan @Component auto-register yok) tek sefer baglanir.
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            LettuceBasedProxyManager<String> proxyManager,
            RateLimitKeyResolver keyResolver,
            RateLimitProperties props,
            ObjectMapper objectMapper) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(
                new RateLimitFilter(proxyManager, keyResolver, props, objectMapper));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setName("rateLimitFilter");
        return registration;
    }
}
