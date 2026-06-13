package com.assetshield.property.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * TIER_LOOKUP_MODE=remote (Day 5): GET marketplace:/internal/users/{id}/tier,
 * cached for 5 minutes. Falls back to FREE (the restrictive tier) when the
 * marketplace is unreachable so limits can never be bypassed by an outage.
 */
public class RemoteSubscriptionTierClient implements SubscriptionTierClient {

    private static final Logger log = LoggerFactory.getLogger(RemoteSubscriptionTierClient.class);

    private final RestClient restClient;
    private final Cache<UUID, String> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(10_000)
            .build();

    public RemoteSubscriptionTierClient(String marketplaceUri, String internalApiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(marketplaceUri)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .build();
    }

    @Override
    public String tierFor(UUID userId) {
        return cache.get(userId, this::fetch);
    }

    @SuppressWarnings("unchecked")
    private String fetch(UUID userId) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri("/internal/users/{id}/tier", userId)
                    .retrieve()
                    .body(Map.class);
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            return String.valueOf(data.get("tier"));
        } catch (Exception e) {
            log.warn("Tier lookup failed for {}; defaulting to FREE: {}", userId, e.getMessage());
            return FREE;
        }
    }
}
