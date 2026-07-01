package com.turkcell.identityservice.application.features.userprofile.query.getme;

import com.turkcell.commonlib.cqrs.Query;
import com.turkcell.identityservice.dto.UserProfileResponse;

/** Giris yapan kullanicinin profilini keycloakId (JWT 'sub') ile getirir (Redis cache'li). */
public record GetMyProfileQuery(String keycloakId) implements Query<UserProfileResponse> {
}
