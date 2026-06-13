package com.assetshield.property.client;

import java.util.UUID;

/**
 * Domain events. EVENTS_MODE selects log (Days 1-5) or remote (Day 6+:
 * POST notification:/internal/events/asset-captured → debounced tip
 * generation).
 */
public interface EventPublisher {

    void assetCaptured(UUID userId, UUID propertyId);
}
