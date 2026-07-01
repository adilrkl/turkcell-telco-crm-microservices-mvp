package com.turkcell.identityservice.application.features.userprofile.command.updateme;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.cqrs.CommandHandler;
import com.turkcell.commonlib.exception.ResourceNotFoundException;
import com.turkcell.identityservice.application.features.userprofile.mapper.UserProfileMapper;
import com.turkcell.identityservice.dto.UserProfileResponse;
import com.turkcell.identityservice.entity.UserProfile;
import com.turkcell.identityservice.repository.UserProfileRepository;

/** Kullanicinin kendi profilinde telefon/dil tercihini gunceller; cache alanlarini temizler. */
@Component
public class UpdateMyProfileCommandHandler implements CommandHandler<UpdateMyProfileCommand, UserProfileResponse> {

    private final UserProfileRepository repository;
    private final UserProfileMapper mapper;

    public UpdateMyProfileCommandHandler(UserProfileRepository repository, UserProfileMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "userProfileByKeycloakId", key = "#command.keycloakId"),
            @CacheEvict(value = "userProfileById", allEntries = true),
            @CacheEvict(value = "userProfilePage", allEntries = true)
    })
    public UserProfileResponse handle(UpdateMyProfileCommand command) {
        UserProfile profile = repository.findByKeycloakId(command.keycloakId())
                .orElseThrow(() -> new ResourceNotFoundException("UserProfile", command.keycloakId()));

        if (command.phoneNumber() != null) {
            profile.setPhoneNumber(command.phoneNumber());
        }
        if (command.preferredLanguage() != null) {
            profile.setPreferredLanguage(command.preferredLanguage());
        }
        return mapper.toResponse(repository.save(profile));
    }
}
