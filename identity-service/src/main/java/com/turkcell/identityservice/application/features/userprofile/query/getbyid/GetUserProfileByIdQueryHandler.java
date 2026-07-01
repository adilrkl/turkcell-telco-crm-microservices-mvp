package com.turkcell.identityservice.application.features.userprofile.query.getbyid;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.turkcell.commonlib.cqrs.QueryHandler;
import com.turkcell.commonlib.exception.ResourceNotFoundException;
import com.turkcell.identityservice.application.features.userprofile.mapper.UserProfileMapper;
import com.turkcell.identityservice.dto.UserProfileResponse;
import com.turkcell.identityservice.repository.UserProfileRepository;

@Component
public class GetUserProfileByIdQueryHandler implements QueryHandler<GetUserProfileByIdQuery, UserProfileResponse> {

    private final UserProfileRepository repository;
    private final UserProfileMapper mapper;

    public GetUserProfileByIdQueryHandler(UserProfileRepository repository, UserProfileMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Cacheable(value = "userProfileById", key = "#query.id")
    public UserProfileResponse handle(GetUserProfileByIdQuery query) {
        return repository.findById(query.id())
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("UserProfile", query.id().toString()));
    }
}
