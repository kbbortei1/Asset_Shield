package com.assetshield.notification.service;

import com.assetshield.notification.client.PropertyClient;
import com.assetshield.notification.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Generation triggers around the TipEngine. The asset-captured event is
 * debounced per property so a bulk documentation session produces one
 * generation run, not thirty.
 */
@Service
public class TipGenerationService {

    private static final Logger log = LoggerFactory.getLogger(TipGenerationService.class);

    private final TipEngine tipEngine;
    private final PropertyClient propertyClient;
    private final AppProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, Instant> lastGeneration = new ConcurrentHashMap<>();

    public TipGenerationService(TipEngine tipEngine, PropertyClient propertyClient,
                                AppProperties properties, Clock clock) {
        this.tipEngine = tipEngine;
        this.propertyClient = propertyClient;
        this.properties = properties;
        this.clock = clock;
    }

    /** Asset-captured trigger: debounced per property. */
    public void onAssetCaptured(UUID propertyId) {
        Instant now = clock.instant();
        Duration window = Duration.ofMinutes(properties.tips().debounceMinutes());
        Instant previous = lastGeneration.get(propertyId);
        if (previous != null && previous.plus(window).isAfter(now)) {
            log.debug("Tip generation debounced for property {} (last run {})", propertyId, previous);
            return;
        }
        lastGeneration.put(propertyId, now);
        generateNow(propertyId);
    }

    /** Undebounced generation (the delivery scheduler keeps feeds fresh). */
    public void generateNow(UUID propertyId) {
        propertyClient.tipsContext(propertyId).ifPresentOrElse(
                tipEngine::generateFor,
                () -> log.warn("Tips context unavailable for property {} — generation skipped",
                        propertyId));
    }
}
