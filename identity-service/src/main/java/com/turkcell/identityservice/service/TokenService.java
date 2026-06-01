package com.turkcell.identityservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.turkcell.identityservice.entity.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
 
@Service
@Slf4j
public class TokenService {
 
    private final SecretKey signingKey;
    private final long accessTokenTtlMs;
    private final long refreshTokenTtlMs;
 
    public TokenService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-ttl-ms:900000}") long accessTokenTtlMs,
            @Value("${app.jwt.refresh-token-ttl-ms:604800000}") long refreshTokenTtlMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlMs = accessTokenTtlMs;
        this.refreshTokenTtlMs = refreshTokenTtlMs;
    }
 
    public String generateAccessToken(User user) {
        List<String> roles = user.getRoles().stream()
                .map(r -> r.getName())
                .collect(Collectors.toList());
 
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusMillis(accessTokenTtlMs)))
                .signWith(signingKey)
                .compact();
    }
 
    public Instant refreshTokenExpiryTime() {
        return Instant.now().plusMillis(refreshTokenTtlMs);
    }
 
    public Optional<Claims> validateAndParse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
