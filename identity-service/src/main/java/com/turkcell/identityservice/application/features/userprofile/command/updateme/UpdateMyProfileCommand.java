package com.turkcell.identityservice.application.features.userprofile.command.updateme;

import com.turkcell.commonlib.cqrs.Command;
import com.turkcell.identityservice.dto.UserProfileResponse;

/**
 * Giris yapan kullanicinin kendi duzenleyebilecegi alanlarini gunceller.
 * {@code keycloakId} controller tarafindan JWT'den; diger alanlar govdeden gelir.
 */
public record UpdateMyProfileCommand(
        String keycloakId,
        String phoneNumber,
        String preferredLanguage) implements Command<UserProfileResponse> {
}
