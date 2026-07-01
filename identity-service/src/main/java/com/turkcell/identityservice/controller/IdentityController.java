package com.turkcell.identityservice.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.turkcell.commonlib.cache.RestPage;
import com.turkcell.commonlib.cqrs.Mediator;
import com.turkcell.commonlib.dto.ApiResponse;
import com.turkcell.identityservice.application.features.userprofile.command.sync.SyncMyProfileCommand;
import com.turkcell.identityservice.application.features.userprofile.command.updateme.UpdateMyProfileCommand;
import com.turkcell.identityservice.application.features.userprofile.query.getbyid.GetUserProfileByIdQuery;
import com.turkcell.identityservice.application.features.userprofile.query.getme.GetMyProfileQuery;
import com.turkcell.identityservice.application.features.userprofile.query.list.ListUserProfilesQuery;
import com.turkcell.identityservice.dto.UpdateMyProfileRequest;
import com.turkcell.identityservice.dto.UserProfileResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/identity/profiles")
public class IdentityController {

    private final Mediator mediator;

    public IdentityController(Mediator mediator) {
        this.mediator = mediator;
    }

    // --- self-service (kimlik dogrulanmis her kullanici) ---

    /** Giris yapan kullanicinin profili (Redis cache'li). Profil yoksa 404 -> once /me/sync. */
    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(mediator.send(new GetMyProfileQuery(jwt.getSubject())));
    }

    /** Profili Keycloak claim'lerinden olusturur/tazeler (ilk giris self-provisioning). */
    @PostMapping("/me/sync")
    public ApiResponse<UserProfileResponse> syncMyProfile(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        if (username == null) {
            username = jwt.getSubject();
        }
        SyncMyProfileCommand command = new SyncMyProfileCommand(
                jwt.getSubject(),
                username,
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name"));
        return ApiResponse.ok(mediator.send(command), "Profil senkronize edildi");
    }

    /** Kullanicinin kendi duzenleyebilecegi alanlari (telefon/dil) gunceller. */
    @PutMapping("/me")
    public ApiResponse<UserProfileResponse> updateMyProfile(@AuthenticationPrincipal Jwt jwt,
                                                            @Valid @RequestBody UpdateMyProfileRequest request) {
        UpdateMyProfileCommand command = new UpdateMyProfileCommand(
                jwt.getSubject(), request.phoneNumber(), request.preferredLanguage());
        return ApiResponse.ok(mediator.send(command), "Profil guncellendi");
    }

    // --- yonetim (CSR/ADMIN) ---

    /** Tekil profil (CSR/ADMIN). */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CSR','ADMIN')")
    public ApiResponse<UserProfileResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(mediator.send(new GetUserProfileByIdQuery(id)));
    }

    /** Sayfali profil listesi (yalnizca ADMIN). */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RestPage<UserProfileResponse>> list(Pageable pageable) {
        return ApiResponse.ok(mediator.send(new ListUserProfilesQuery(pageable)));
    }
}
