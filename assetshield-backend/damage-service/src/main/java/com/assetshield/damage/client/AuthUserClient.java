package com.assetshield.damage.client;

import com.assetshield.damage.config.AppProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** Owner name lookups for dossier covers (auth internal API, cached 5 min). */
@Component
public class AuthUserClient {

    public record AuthUserInfo(UUID id, String fullName, String phoneNumber) {
    }

    private final RestClient restClient;
    private final Cache<UUID, Optional<AuthUserInfo>> cache = Caffeine.newBuilder()
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
        return cache.get(userId, this::fetch);
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
                    String.valueOf(data.get("phoneNumber"))));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
