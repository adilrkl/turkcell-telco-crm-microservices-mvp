package com.turkcell.identityservice.application.features.userprofile.command.sync;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.cqrs.CommandHandler;
import com.turkcell.identityservice.application.features.userprofile.mapper.UserProfileMapper;
import com.turkcell.identityservice.application.features.userprofile.rule.UserProfileBusinessRules;
import com.turkcell.identityservice.dto.UserProfileResponse;
import com.turkcell.identityservice.entity.UserProfile;
import com.turkcell.identityservice.repository.UserProfileRepository;

/**
 * Kullanici profilini upsert eder: keycloakId ile varsa kimlik alanlarini tazeler,
 * yoksa (ilk giris) yeni profil olusturur. Ilgili cache alanlarini temizler.
 */
@Component
public class SyncMyProfileCommandHandler implements CommandHandler<SyncMyProfileCommand, UserProfileResponse> {

    private final UserProfileRepository repository;
    private final UserProfileBusinessRules rules;
    private final UserProfileMapper mapper;

    public SyncMyProfileCommandHandler(UserProfileRepository repository,
                                       UserProfileBusinessRules rules,
                                       UserProfileMapper mapper) {
        this.repository = repository;
        this.rules = rules;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "userProfileByKeycloakId", key = "#command.keycloakId"),
            @CacheEvict(value = "userProfileById", allEntries = true),
            @CacheEvict(value = "userProfilePage", allEntries = true)
    })
    public UserProfileResponse handle(SyncMyProfileCommand command) {
        // Kimlik alanlari her sync'te Keycloak'tan yazilir; bu yuzden hem ilk olusturmada hem
        // re-sync'te (Keycloak tarafinda username/email degismisse) sahibi haric benzersizlik
        // kontrol edilir -> cakisma DB constraint (500) yerine temiz 409 ProfileConflictException.
        rules.usernameMustBeUnique(command.username(), command.keycloakId());
        rules.emailMustBeUnique(command.email(), command.keycloakId());

        UserProfile profile = repository.findByKeycloakId(command.keycloakId())
                .map(existing -> {
                    mapper.applyIdentityClaims(existing, command);
                    return existing;
                })
                .orElseGet(() -> mapper.toNewProfile(command));
        return mapper.toResponse(repository.save(profile));
    }
}
