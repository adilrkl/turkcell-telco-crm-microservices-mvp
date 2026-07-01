package com.turkcell.identityservice.dto;

import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String keycloakId,
        String username,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        String preferredLanguage,
        String status) {
}
