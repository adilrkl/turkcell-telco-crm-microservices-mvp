package com.turkcell.identityservice.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.turkcell.identityservice.dto.LoginRequest;
import com.turkcell.identityservice.dto.RegisterRequest;
import com.turkcell.identityservice.entity.Role;
import com.turkcell.identityservice.entity.User;
import com.turkcell.identityservice.entity.UserStatus;
import com.turkcell.identityservice.exception.AuthException;
import com.turkcell.identityservice.repository.RefreshTokenRepository;
import com.turkcell.identityservice.repository.RoleRepository;
import com.turkcell.identityservice.repository.UserRepository;
import com.turkcell.identityservice.service.AuthService;
import com.turkcell.identityservice.service.TokenService;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
 
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
 
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
 
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock TokenService tokenService;
 
    @InjectMocks AuthService authService;
 
    private Role defaultRole;
    private User activeUser;
 
    @BeforeEach
    void setUp() {
        defaultRole = Role.builder().id(UUID.randomUUID()).name("ROLE_USER").build();
        activeUser = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("test@example.com")
                .passwordHash("$2a$hashed")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(defaultRole))
                .build();
    }
 
    @Test
    void register_shouldCreateUser_whenUsernameAndEmailAreUnique() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(defaultRole));
        when(passwordEncoder.encode("password123")).thenReturn("$2a$hashed");
        when(userRepository.save(any(User.class))).thenReturn(activeUser);
 
        var request = new RegisterRequest("testuser", "test@example.com", "password123");
        var response = authService.register(request);
 
        assertThat(response.username()).isEqualTo("testuser");
        verify(userRepository).save(any(User.class));
    }
 
    @Test
    void register_shouldThrow_whenUsernameAlreadyExists() {
        when(userRepository.existsByUsername("testuser")).thenReturn(true);
 
        var request = new RegisterRequest("testuser", "test@example.com", "password123");
 
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Username already taken");
    }
 
    @Test
    void login_shouldReturnTokens_whenCredentialsAreValid() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("password123", "$2a$hashed")).thenReturn(true);
        when(tokenService.generateAccessToken(activeUser)).thenReturn("access.token.here");
        when(tokenService.refreshTokenExpiryTime()).thenReturn(Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
 
        var response = authService.login(new LoginRequest("testuser", "password123"));
 
        assertThat(response.accessToken()).isEqualTo("access.token.here");
        assertThat(response.refreshToken()).isNotBlank();
    }
 
    @Test
    void login_shouldThrow_whenPasswordIsWrong() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrongpass", "$2a$hashed")).thenReturn(false);
 
        assertThatThrownBy(() -> authService.login(new LoginRequest("testuser", "wrongpass")))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Invalid username or password");
    }
 
    @Test
    void login_shouldThrow_whenUserNotFound() {
        when(userRepository.findByUsername("noone")).thenReturn(Optional.empty());
 
        assertThatThrownBy(() -> authService.login(new LoginRequest("noone", "pass")))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Invalid username or password");
    }
}
