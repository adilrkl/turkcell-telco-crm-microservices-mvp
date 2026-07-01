package com.turkcell.identityservice.application.features.userprofile.query.getbyid;

import java.util.UUID;

import com.turkcell.commonlib.cqrs.Query;
import com.turkcell.identityservice.dto.UserProfileResponse;

/** Tekil profil sorgusu (Redis cache'li) - CSR/ADMIN. */
public record GetUserProfileByIdQuery(UUID id) implements Query<UserProfileResponse> {
}
