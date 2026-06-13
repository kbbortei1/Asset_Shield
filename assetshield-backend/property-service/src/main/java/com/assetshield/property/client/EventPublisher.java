package com.assetshield.property.client;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Domain events. Log-only today; Day 6 wires assetCaptured into the tips
 * engine.
 */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    public void assetCaptured(UUID userId, UUID propertyId) {
        log.info("EVENT assetCaptured: user={} property={}", userId, propertyId);
    }
}
