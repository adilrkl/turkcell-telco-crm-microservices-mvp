package com.turkcell.notificationservice.dto;

import java.time.Instant;

/** Iletisim tercihi cevabi (FR-30). */
public record NotificationPreferenceResponse(
        String channel,
        boolean enabled,
        Instant updatedAt) {
}
