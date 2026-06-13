package com.assetshield.property.client;

import java.util.UUID;

/**
 * Marketplace opt-in signal. MARKETPLACE_EVENTS_MODE selects log (Days 1-4)
 * or remote (Day 5+: POST marketplace:/internal/marketplace/optin-changed).
 */
public interface MarketplaceEventsClient {

    void optInChanged(UUID propertyId, boolean openToOffers);
}
