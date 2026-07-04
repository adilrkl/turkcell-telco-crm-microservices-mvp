package com.turkcell.notificationservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Iletisim tercihi ayarlama govdesi (FR-30). */
public record SetPreferenceRequest(
        @Pattern(regexp = "SMS|EMAIL|PUSH", message = "channel: SMS|EMAIL|PUSH") String channel,
        @NotNull Boolean enabled) {
}
