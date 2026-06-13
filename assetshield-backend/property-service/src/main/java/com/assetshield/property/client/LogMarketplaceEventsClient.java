package com.assetshield.property.client;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** MARKETPLACE_EVENTS_MODE=log: records the would-be push at INFO. */
public class LogMarketplaceEventsClient implements MarketplaceEventsClient {

    private static final Logger log = LoggerFactory.getLogger(LogMarketplaceEventsClient.class);

    @Override
    public void optInChanged(UUID propertyId, boolean openToOffers) {
        log.info("MARKETPLACE optInChanged: property={} openToOffers={}", propertyId, openToOffers);
    }
}
