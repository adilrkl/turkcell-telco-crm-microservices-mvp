package com.turkcell.notificationservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.turkcell.commonlib.dto.ApiResponse;
import com.turkcell.notificationservice.dto.NotificationPreferenceResponse;
import com.turkcell.notificationservice.dto.SetPreferenceRequest;
import com.turkcell.notificationservice.entity.NotificationPreference;
import com.turkcell.notificationservice.service.NotificationPreferenceService;

import jakarta.validation.Valid;

/**
 * Iletisim tercihi (opt-in/opt-out) yonetimi (FR-30). Bildirim gonderimi event-driven'dir;
 * bu REST yolu yalnizca tercih okuma/yazmadir. Platformda kullanici<->musteri baglantisi
 * olmadigi icin CSR/ADMIN musteri adina yonetir (diger endpoint'lerle ayni gerekce).
 */
@RestController
@RequestMapping("/api/notifications/preferences")
public class NotificationPreferenceController {

    private final NotificationPreferenceService service;

    public NotificationPreferenceController(NotificationPreferenceService service) {
        this.service = service;
    }

    /** Kullanicinin tanimli kanal tercihleri (tanimsiz kanallar varsayilan opt-in). */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('CSR','ADMIN')")
    public ApiResponse<List<NotificationPreferenceResponse>> get(@PathVariable UUID userId) {
        List<NotificationPreferenceResponse> prefs = service.getPreferences(userId).stream()
                .map(NotificationPreferenceController::toResponse)
                .toList();
        return ApiResponse.ok(prefs);
    }

    /** Bir kanalin opt-in/opt-out tercihini ayarlar (upsert). */
    @PutMapping("/{userId}")
    @PreAuthorize("hasAnyRole('CSR','ADMIN')")
    public ApiResponse<NotificationPreferenceResponse> set(@PathVariable UUID userId,
                                                           @Valid @RequestBody SetPreferenceRequest request) {
        NotificationPreference saved = service.setPreference(userId, request.channel(), request.enabled());
        return ApiResponse.ok(toResponse(saved), request.enabled() ? "Kanal acildi (opt-in)" : "Kanal kapatildi (opt-out)");
    }

    private static NotificationPreferenceResponse toResponse(NotificationPreference p) {
        return new NotificationPreferenceResponse(p.getChannel(), p.isEnabled(), p.getUpdatedAt());
    }
}
