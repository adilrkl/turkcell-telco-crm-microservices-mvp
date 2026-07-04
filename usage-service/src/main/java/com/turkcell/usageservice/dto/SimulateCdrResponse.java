package com.turkcell.usageservice.dto;

import java.util.List;
import java.util.UUID;

/** CDR simulatoru sonucu: usage-events'e publish edilen event'lerin ozeti. */
public record SimulateCdrResponse(
        UUID subscriptionId,
        int published,
        List<String> cdrRefs) {
}
