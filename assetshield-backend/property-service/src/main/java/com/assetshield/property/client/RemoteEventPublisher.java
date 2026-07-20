package com.assetshield.property.client;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * EVENTS_MODE=remote (Day 6+): pushes asset-captured to notification-service
 * for debounced tip generation. Best-effort — a dead notification-service
 * must NEVER fail an asset upload; failures log WARN and move on.
 */
public class RemoteEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RemoteEventPublisher.class);

    private final RestClient restClient;

    public RemoteEventPublisher(String notificationUri, String internalApiKey) {
        this.restClient = RestClient.builder()
                .requestFactory(InternalHttp.requestFactory())
                .baseUrl(notificationUri)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .build();
    }

    @Override
    public void assetCaptured(UUID userId, UUID propertyId) {
        try {
            restClient.post()
                    .uri("/internal/events/asset-captured")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("userId", userId.toString(),
                            "propertyId", propertyId.toString()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("assetCaptured event push failed for property {}: {}", propertyId, e.getMessage());
        }
    }
}
