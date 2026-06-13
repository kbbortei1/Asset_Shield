package com.assetshield.property.client;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** EVENTS_MODE=log: records the would-be event at INFO. */
public class LogEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LogEventPublisher.class);

    @Override
    public void assetCaptured(UUID userId, UUID propertyId) {
        log.info("EVENT assetCaptured: user={} property={}", userId, propertyId);
    }
}
