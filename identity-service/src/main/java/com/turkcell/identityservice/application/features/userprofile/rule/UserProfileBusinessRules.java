package com.turkcell.identityservice.application.features.userprofile.rule;

import org.springframework.stereotype.Component;

import com.turkcell.identityservice.exception.ProfileConflictException;
import com.turkcell.identityservice.repository.UserProfileRepository;

/** Profil is kurallari. */
@Component
public class UserProfileBusinessRules {

    private final UserProfileRepository repository;

    public UserProfileBusinessRules(UserProfileRepository repository) {
        this.repository = repository;
    }

    /** username, sahibi ({@code ownerKeycloakId}) disindaki bir profile ait olmamali (create + re-sync). */
    public void usernameMustBeUnique(String username, String ownerKeycloakId) {
        if (repository.existsByUsernameAndKeycloakIdNot(username, ownerKeycloakId)) {
            throw new ProfileConflictException("username zaten kayitli: " + username);
        }
    }

    /** email (varsa), sahibi ({@code ownerKeycloakId}) disindaki bir profile ait olmamali (create + re-sync). */
    public void emailMustBeUnique(String email, String ownerKeycloakId) {
        if (email != null && repository.existsByEmailAndKeycloakIdNot(email, ownerKeycloakId)) {
            throw new ProfileConflictException("email zaten kayitli: " + email);
        }
    }
}
