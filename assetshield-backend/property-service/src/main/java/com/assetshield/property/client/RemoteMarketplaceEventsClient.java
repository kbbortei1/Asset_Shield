package com.assetshield.property.client;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * MARKETPLACE_EVENTS_MODE=remote (Day 5+): POST
 * marketplace:/internal/marketplace/optin-changed. Best-effort — a
 * marketplace outage must never block an owner's opt-in/opt-out; pending
 * interests on an unreachable opt-out are cleaned up when express-interest
 * re-validates against the property's live opt-in flag.
 */
public class RemoteMarketplaceEventsClient implements MarketplaceEventsClient {

    private static final Logger log = LoggerFactory.getLogger(RemoteMarketplaceEventsClient.class);

    private final RestClient restClient;

    public RemoteMarketplaceEventsClient(String marketplaceUri, String internalApiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(marketplaceUri)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .build();
    }

    @Override
    public void optInChanged(UUID propertyId, boolean openToOffers) {
        try {
            restClient.post()
                    .uri("/internal/marketplace/optin-changed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("propertyId", propertyId.toString(),
                            "openToOffers", openToOffers))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("optin-changed push failed for property {}: {}", propertyId, e.getMessage());
        }
    }
}
