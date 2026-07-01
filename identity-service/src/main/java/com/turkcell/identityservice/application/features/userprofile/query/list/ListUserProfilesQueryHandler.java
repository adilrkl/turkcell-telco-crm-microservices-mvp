package com.turkcell.identityservice.application.features.userprofile.query.list;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.turkcell.commonlib.cache.RestPage;
import com.turkcell.commonlib.cqrs.QueryHandler;
import com.turkcell.identityservice.application.features.userprofile.mapper.UserProfileMapper;
import com.turkcell.identityservice.dto.UserProfileResponse;
import com.turkcell.identityservice.repository.UserProfileRepository;

@Component
public class ListUserProfilesQueryHandler implements QueryHandler<ListUserProfilesQuery, RestPage<UserProfileResponse>> {

    private final UserProfileRepository repository;
    private final UserProfileMapper mapper;

    public ListUserProfilesQueryHandler(UserProfileRepository repository, UserProfileMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Cacheable(value = "userProfilePage", key = "'p=' + #query.pageable.pageNumber + ';s=' + #query.pageable.pageSize")
    public RestPage<UserProfileResponse> handle(ListUserProfilesQuery query) {
        return new RestPage<>(repository.findAll(query.pageable()).map(mapper::toResponse));
    }
}
