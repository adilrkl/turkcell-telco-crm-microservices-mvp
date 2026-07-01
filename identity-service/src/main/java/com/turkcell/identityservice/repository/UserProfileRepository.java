package com.turkcell.identityservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.turkcell.identityservice.entity.UserProfile;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByKeycloakId(String keycloakId);

    /** Ayni username baska bir profile (keycloakId != owner) ait mi? Self-sync'te kendini haric tutar. */
    boolean existsByUsernameAndKeycloakIdNot(String username, String keycloakId);

    /** Ayni email baska bir profile (keycloakId != owner) ait mi? Self-sync'te kendini haric tutar. */
    boolean existsByEmailAndKeycloakIdNot(String email, String keycloakId);
}
