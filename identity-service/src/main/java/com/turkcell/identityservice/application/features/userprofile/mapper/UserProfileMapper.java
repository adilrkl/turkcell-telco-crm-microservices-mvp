package com.turkcell.identityservice.application.features.userprofile.mapper;

import org.springframework.stereotype.Component;

import com.turkcell.identityservice.application.features.userprofile.command.sync.SyncMyProfileCommand;
import com.turkcell.identityservice.dto.UserProfileResponse;
import com.turkcell.identityservice.entity.UserProfile;

/** UserProfile entity <-> command/response donusumleri. */
@Component
public class UserProfileMapper {

    /** Ilk giriste Keycloak claim'lerinden yeni profil olusturur (status/preferredLanguage entity default'u). */
    public UserProfile toNewProfile(SyncMyProfileCommand command) {
        UserProfile profile = new UserProfile();
        profile.setKeycloakId(command.keycloakId());
        applyIdentityClaims(profile, command);
        return profile;
    }

    /** Keycloak'tan gelen kimlik alanlarini (username/email/ad/soyad) mevcut profile yansitir. */
    public void applyIdentityClaims(UserProfile profile, SyncMyProfileCommand command) {
        profile.setUsername(command.username());
        profile.setEmail(command.email());
        profile.setFirstName(command.firstName());
        profile.setLastName(command.lastName());
    }

    public UserProfileResponse toResponse(UserProfile p) {
        return new UserProfileResponse(
                p.getId(),
                p.getKeycloakId(),
                p.getUsername(),
                p.getEmail(),
                p.getFirstName(),
                p.getLastName(),
                p.getPhoneNumber(),
                p.getPreferredLanguage(),
                p.getStatus());
    }
}
