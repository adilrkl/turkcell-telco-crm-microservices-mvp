package com.turkcell.gatewayserver.ratelimit;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Rate-limit bucket anahtarini cikarir.
 *
 * <p>Oncelik: kimlik dogrulanmis kullanici (docx §13 "user basina 100 req/min").
 * Gateway JWT'yi <b>imza dogrulamadan</b> sadece {@code sub} claim'i icin cozer
 * (downstream resource-server'lar imzayi zaten dogruluyor). Bu yetkilendirme
 * degil, yalnizca sayac anahtaridir; sahte token en kotu ihtimalle saldirgani
 * kendi user-bucket'ina yazar, ayrica IP-bucket'i da uygulanir.</p>
 *
 * <p>Token yoksa (anonim/login/public uc) istemci IP'sine duser; proxy arkasinda
 * {@code X-Forwarded-For} ilk atlama kullanilir.</p>
 */
@Component
public class RateLimitKeyResolver {

    private final ObjectMapper objectMapper;

    public RateLimitKeyResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Donen anahtar Redis namespace'i icerir: "user:&lt;sub&gt;" ya da "ip:&lt;addr&gt;". */
    public String resolve(HttpServletRequest request) {
        String sub = subjectFromBearer(request.getHeader("Authorization"));
        if (sub != null) {
            return "user:" + sub;
        }
        return "ip:" + clientIp(request);
    }

    private String subjectFromBearer(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring(7).trim();
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode claims = objectMapper.readTree(new String(payload, StandardCharsets.UTF_8));
            JsonNode sub = claims.get("sub");
            return (sub != null && sub.isTextual() && !sub.asText().isBlank()) ? sub.asText() : null;
        } catch (Exception ex) {
            // Bozuk/parse edilemeyen token -> IP'ye fallback.
            return null;
        }
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(real)) {
            return real.trim();
        }
        return request.getRemoteAddr();
    }
}
