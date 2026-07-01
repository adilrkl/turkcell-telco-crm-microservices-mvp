package com.turkcell.identityservice.application.features.userprofile.command.sync;

import com.turkcell.commonlib.cqrs.Command;
import com.turkcell.identityservice.dto.UserProfileResponse;

/**
 * Giris yapan kullanicinin profilini Keycloak claim'lerinden olusturur/gunceller (upsert).
 * Tum alanlar controller tarafindan JWT'den turetilir (govde yok).
 */
public record SyncMyProfileCommand(
        String keycloakId,
        String username,
        String email,
        String firstName,
        String lastName) implements Command<UserProfileResponse> {
}
