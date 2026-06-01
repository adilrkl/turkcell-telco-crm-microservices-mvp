package com.turkcell.identityservice.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.turkcell.identityservice.entity.Role;
import com.turkcell.identityservice.entity.User;
import com.turkcell.identityservice.entity.UserStatus;
import com.turkcell.identityservice.service.TokenService;

import java.util.Set;
import java.util.UUID;
 
import static org.assertj.core.api.Assertions.assertThat;
 
class TokenServiceTest {
 
    private TokenService tokenService;
    private User testUser;
 
    @BeforeEach
    void setUp() {
        // 32+ karakter secret
        tokenService = new TokenService(
                "test-secret-key-that-is-long-enough-for-hmac-sha256",
                900_000L,
                604_800_000L
        );
        testUser = User.builder()
                .id(UUID.randomUUID())
                .username("tokenuser")
                .email("token@example.com")
                .passwordHash("hash")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(Role.builder().name("ROLE_USER").build()))
                .build();
    }
 
    @Test
    void generateAccessToken_shouldProduceValidToken() {
        String token = tokenService.generateAccessToken(testUser);
 
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); // header.payload.signature
    }
 
    @Test
    void validateAndParse_shouldReturnClaims_forValidToken() {
        String token = tokenService.generateAccessToken(testUser);
        var claims = tokenService.validateAndParse(token);
 
        assertThat(claims).isPresent();
        assertThat(claims.get().getSubject()).isEqualTo(testUser.getId().toString());
        assertThat(claims.get().get("username", String.class)).isEqualTo("tokenuser");
    }
 
    @Test
    void validateAndParse_shouldReturnEmpty_forTamperedToken() {
        String token = tokenService.generateAccessToken(testUser) + "tampered";
        var claims = tokenService.validateAndParse(token);
 
        assertThat(claims).isEmpty();
    }
}
