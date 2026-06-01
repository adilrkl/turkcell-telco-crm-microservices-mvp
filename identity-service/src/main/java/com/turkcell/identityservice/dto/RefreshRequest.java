package com.turkcell.identityservice.dto;

import jakarta.validation.constraints.NotBlank;
 
public record RefreshRequest(@NotBlank String refreshToken) {}
