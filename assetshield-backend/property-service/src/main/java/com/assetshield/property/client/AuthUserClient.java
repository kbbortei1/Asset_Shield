package com.assetshield.property.client;

import com.assetshield.property.config.AppProperties;
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
 * are cached for 5 minutes (names on invitations/member lists); lookups by
 * phone are NOT cached so a just-registered invitee resolves immediately.
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
                .requestFactory(InternalHttp.requestFactory())
                .baseUrl(properties.authServiceUri())
                .defaultHeader("X-Internal-Api-Key", properties.internalApiKey())
                .build();
    }

    public Optional<AuthUserInfo> byId(UUID userId) {
        return byIdCache.get(userId, id -> fetch("/internal/users/{key}", id.toString()));
    }

    public Optional<AuthUserInfo> byPhone(String phone) {
        return fetch("/internal/users/by-phone/{key}", phone);
    }

    @SuppressWarnings("unchecked")
    private Optional<AuthUserInfo> fetch(String uriTemplate, String key) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri(uriTemplate, key)
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
