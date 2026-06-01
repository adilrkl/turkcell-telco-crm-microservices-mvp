package com.turkcell.identityservice.dto;

import jakarta.validation.constraints.NotBlank;
 
public record LogoutRequest(@NotBlank String refreshToken) {}
