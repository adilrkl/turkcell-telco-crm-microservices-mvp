package com.turkcell.identityservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.identityservice.dto.LoginRequest;
import com.turkcell.identityservice.dto.LoginResponse;
import com.turkcell.identityservice.dto.LogoutRequest;
import com.turkcell.identityservice.dto.RefreshRequest;
import com.turkcell.identityservice.dto.RegisterRequest;
import com.turkcell.identityservice.dto.RegisterResponse;
import com.turkcell.identityservice.entity.RefreshToken;
import com.turkcell.identityservice.entity.Role;
import com.turkcell.identityservice.entity.User;
import com.turkcell.identityservice.entity.UserStatus;
import com.turkcell.identityservice.exception.AuthException;
import com.turkcell.identityservice.repository.RefreshTokenRepository;
import com.turkcell.identityservice.repository.RoleRepository;
import com.turkcell.identityservice.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
 
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
 
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
 
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw AuthException.usernameAlreadyExists(request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw AuthException.emailAlreadyExists(request.email());
        }
 
        Role defaultRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("Default role ROLE_USER not found in DB"));
 
        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(UserStatus.ACTIVE)
                .roles(Set.of(defaultRole))
                .build();
 
        User saved = userRepository.save(user);
        log.info("New user registered: {} ({})", saved.getUsername(), saved.getId());
        return new RegisterResponse(saved.getId(), saved.getUsername(), saved.getEmail());
    }
 
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> AuthException.invalidCredentials());
 
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw AuthException.invalidCredentials();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw AuthException.accountNotActive(user.getStatus());
        }
 
        String accessToken = tokenService.generateAccessToken(user);
        String rawRefresh = UUID.randomUUID().toString();
 
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hash(rawRefresh))
                .expiresAt(tokenService.refreshTokenExpiryTime())
                .build();
        refreshTokenRepository.save(refreshToken);
 
        log.info("User logged in: {}", user.getUsername());
        return new LoginResponse(accessToken, rawRefresh);
    }
 
    @Transactional
    public LoginResponse refresh(RefreshRequest request) {
        String tokenHash = hash(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> AuthException.invalidRefreshToken());
 
        if (!stored.isValid()) {
            throw AuthException.invalidRefreshToken();
        }
 
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);
 
        User user = stored.getUser();
        String newAccess = tokenService.generateAccessToken(user);
        String newRawRefresh = UUID.randomUUID().toString();
 
        RefreshToken newRefreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hash(newRawRefresh))
                .expiresAt(tokenService.refreshTokenExpiryTime())
                .build();
        refreshTokenRepository.save(newRefreshToken);
 
        return new LoginResponse(newAccess, newRawRefresh);
    }
 
    @Transactional
    public void logout(LogoutRequest request) {
        String tokenHash = hash(request.refreshToken());
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(t -> {
                    t.setRevoked(true);
                    refreshTokenRepository.save(t);
                    log.info("User {} logged out", t.getUser().getUsername());
                });
    }

    @Transactional
    public void assignRole(UUID userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));
 
        user.getRoles().add(role);
        userRepository.save(user);
        log.info("Assigned role {} to user {}", roleName, user.getUsername());
    }
 
    private String hash(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
