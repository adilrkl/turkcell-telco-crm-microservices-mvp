package com.turkcell.identityservice.application.features.userprofile.query.list;

import org.springframework.data.domain.Pageable;

import com.turkcell.commonlib.cache.RestPage;
import com.turkcell.commonlib.cqrs.Query;
import com.turkcell.identityservice.dto.UserProfileResponse;

/** Sayfali profil listesi sorgusu (Redis cache'li) - ADMIN. */
public record ListUserProfilesQuery(Pageable pageable) implements Query<RestPage<UserProfileResponse>> {
}
