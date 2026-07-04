package com.turkcell.usageservice.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.turkcell.commonlib.dto.ApiResponse;
import com.turkcell.commonlib.saga.SagaHeaders;
import com.turkcell.usageservice.dto.SimulateCdrRequest;
import com.turkcell.usageservice.dto.SimulateCdrResponse;
import com.turkcell.usageservice.event.UsageRecordedEvent;

import jakarta.validation.Valid;

import tools.jackson.databind.ObjectMapper;

/**
 * CDR simulatoru (G1, docx senaryo 14.3): mediation/CDR sistemini taklit ederek
 * usage-events topic'ine rastgele UsageRecorded event'leri publish eder; kayitlar
 * gercek Kafka yolundan (consumer -> inbox -> kota dusumu) akar.
 * SADECE dev profilinde yuklenir (profil korumali) + ADMIN rolu ister.
 * Dogrudan publish bilinclidir: simulator DIS sistemi taklit eder, outbox garantisi gerekmez.
 */
@RestController
@RequestMapping("/api/usage/simulate")
@Profile("dev")
public class CdrSimulatorController {

    private static final Logger log = LoggerFactory.getLogger(CdrSimulatorController.class);
    private static final String[] TYPES = {"VOICE", "SMS", "DATA"};

    private final StreamBridge streamBridge;
    private final ObjectMapper objectMapper;

    public CdrSimulatorController(StreamBridge streamBridge, ObjectMapper objectMapper) {
        this.streamBridge = streamBridge;
        this.objectMapper = objectMapper;
    }

    /** count adet (default 10) rastgele CDR uretir: VOICE 1-30 dk, SMS 1 adet, DATA 50-1024 MB. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SimulateCdrResponse> simulate(@Valid @RequestBody SimulateCdrRequest request) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<String> cdrRefs = new ArrayList<>();

        for (int i = 0; i < request.countOrDefault(); i++) {
            String type = TYPES[random.nextInt(TYPES.length)];
            BigDecimal quantity = switch (type) {
                case "VOICE" -> BigDecimal.valueOf(random.nextDouble(1, 30)).setScale(2, RoundingMode.HALF_UP);
                case "SMS" -> BigDecimal.ONE;
                default -> BigDecimal.valueOf(random.nextDouble(50, 1024)).setScale(2, RoundingMode.HALF_UP);
            };
            String cdrRef = "SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            UsageRecordedEvent event = new UsageRecordedEvent(UUID.randomUUID(),
                    request.subscriptionId(), type, quantity, Instant.now(), cdrRef);

            streamBridge.send("usage-events",
                    MessageBuilder.withPayload(objectMapper.writeValueAsString(event).getBytes(StandardCharsets.UTF_8))
                            .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON_VALUE)
                            .setHeader(SagaHeaders.EVENT_TYPE, "UsageRecorded")
                            .build());
            cdrRefs.add(cdrRef);
        }

        log.info("cdr-sim: {} event publish edildi. sub={}", cdrRefs.size(), request.subscriptionId());
        return ApiResponse.ok(new SimulateCdrResponse(request.subscriptionId(), cdrRefs.size(), cdrRefs),
                "CDR simulasyonu tamamlandi");
    }
}
