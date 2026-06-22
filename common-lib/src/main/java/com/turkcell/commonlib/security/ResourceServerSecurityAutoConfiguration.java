package com.turkcell.commonlib.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Tum telco resource-server'lar icin ortak guvenlik konfigurasyonu.
 * - Yalnizca oauth2-resource-server starter'i classpath'te ise aktiflesir
 *   ({@code @ConditionalOnClass(BearerTokenAuthenticationToken.class)}).
 * - {@code /actuator/**} ve dokumantasyon asset/spec yollari acik,
 *   geri kalan her istek JWT ile dogrulanmis olmali.
 * - Rol bazli yetki icin {@code @PreAuthorize} (@EnableMethodSecurity) +
 *   {@link KeycloakRealmRoleConverter}.
 * - STATELESS, CSRF kapali (token bazli, cookie/session yok).
 * Servisler kendi {@code SecurityFilterChain} bean'ini tanimlayarak override edebilir
 * ({@code @ConditionalOnMissingBean}).
 */
@AutoConfiguration
@ConditionalOnClass(BearerTokenAuthenticationToken.class)
@EnableWebSecurity
@EnableMethodSecurity
@Import(AccessDeniedExceptionHandler.class)
public class ResourceServerSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain telcoResourceServerFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/**",
                                "/error",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    public static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }
}
