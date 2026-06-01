package com.turkcell.identityservice.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.turkcell.commonlib.dto.ApiResponse;
import com.turkcell.identityservice.dto.AssignRoleRequest;
import com.turkcell.identityservice.dto.LoginRequest;
import com.turkcell.identityservice.dto.LoginResponse;
import com.turkcell.identityservice.dto.LogoutRequest;
import com.turkcell.identityservice.dto.RefreshRequest;
import com.turkcell.identityservice.dto.RegisterRequest;
import com.turkcell.identityservice.dto.RegisterResponse;
import com.turkcell.identityservice.service.AuthService;
 
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
 
    private final AuthService authService;
 
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request), "User registered successfully");
    }
 
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }
 
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request));
    }
 
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
    }

    @PutMapping("/users/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> assignRole(
            @PathVariable UUID userId,
            @RequestBody AssignRoleRequest request) {
        authService.assignRole(userId, request.roleName());
        return ApiResponse.ok(null, "Role assigned");
}
}
