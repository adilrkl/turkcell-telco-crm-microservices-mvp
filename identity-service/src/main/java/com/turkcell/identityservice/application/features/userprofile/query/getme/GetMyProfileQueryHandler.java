package com.turkcell.identityservice.application.features.userprofile.query.getme;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.turkcell.commonlib.cqrs.QueryHandler;
import com.turkcell.commonlib.exception.ResourceNotFoundException;
import com.turkcell.identityservice.application.features.userprofile.mapper.UserProfileMapper;
import com.turkcell.identityservice.dto.UserProfileResponse;
import com.turkcell.identityservice.repository.UserProfileRepository;

@Component
public class GetMyProfileQueryHandler implements QueryHandler<GetMyProfileQuery, UserProfileResponse> {

    private final UserProfileRepository repository;
    private final UserProfileMapper mapper;

    public GetMyProfileQueryHandler(UserProfileRepository repository, UserProfileMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Cacheable(value = "userProfileByKeycloakId", key = "#query.keycloakId")
    public UserProfileResponse handle(GetMyProfileQuery query) {
        return repository.findByKeycloakId(query.keycloakId())
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("UserProfile", query.keycloakId()));
    }
}
