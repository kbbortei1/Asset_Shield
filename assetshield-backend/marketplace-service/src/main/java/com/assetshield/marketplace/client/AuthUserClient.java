package com.assetshield.marketplace.client;

import com.assetshield.marketplace.config.AppProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Internal lookups against auth-service (X-Internal-Api-Key). Lookups by id
 * are cached for 5 minutes (agent/owner names on lists are read far more
 * often than they change).
 */
@Component
public class AuthUserClient {

    public record AuthUserInfo(UUID id, String fullName, String phoneNumber, String role, String status) {
    }

    private final RestClient restClient;
    private final Cache<UUID, Optional<AuthUserInfo>> byIdCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(10_000)
            .build();

    public AuthUserClient(AppProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.authServiceUri())
                .defaultHeader("X-Internal-Api-Key", properties.internalApiKey())
                .build();
    }

    public Optional<AuthUserInfo> byId(UUID userId) {
        return byIdCache.get(userId, this::fetch);
    }

    @SuppressWarnings("unchecked")
    private Optional<AuthUserInfo> fetch(UUID userId) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri("/internal/users/{id}", userId)
                    .retrieve()
                    .body(Map.class);
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            return Optional.of(new AuthUserInfo(
                    UUID.fromString(String.valueOf(data.get("id"))),
                    String.valueOf(data.get("fullName")),
                    String.valueOf(data.get("phoneNumber")),
                    String.valueOf(data.get("role")),
                    String.valueOf(data.get("status"))));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
