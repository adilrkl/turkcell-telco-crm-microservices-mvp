package com.turkcell.identityservice.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Kullanicinin kendi profilinde degistirebilecegi alanlar (PUT /me).
 * Kimlik alanlari (username/email/ad/soyad) Keycloak'tan sync ile gelir, burada duzenlenmez.
 */
public record UpdateMyProfileRequest(
        @Size(max = 30) String phoneNumber,
        @Pattern(regexp = "tr|en", message = "preferredLanguage yalnizca 'tr' veya 'en' olabilir")
        String preferredLanguage) {
}
