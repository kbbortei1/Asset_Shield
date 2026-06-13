package com.assetshield.damage.client;

import com.assetshield.damage.config.AppProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * Internal lookups against property-service (X-Internal-Api-Key). Access
 * resolutions are cached for 60 seconds per (propertyId, userId); everything
 * else is fetched fresh — pairing correctness beats latency.
 */
@Component
public class PropertyInternalClient {

    public record PropertyInfo(UUID id, UUID ownerUserId, String name, String type,
                               String locality, boolean openToOffers, boolean deleted) {
    }

    public record AssetInfo(UUID id, UUID propertyId, String objectPath, String sha256Hash,
                            BigDecimal gpsLat, BigDecimal gpsLng, Instant capturedAt,
                            String description, BigDecimal estimatedValue, String category) {
    }

    public record AssetNear(UUID assetId, double distanceMeters, String description,
                            BigDecimal estimatedValue, String category, String thumbnailUrl,
                            String sha256Hash, Instant capturedAt) {
    }

    private record AccessKey(UUID propertyId, UUID userId) {
    }

    private final RestClient restClient;
    private final Cache<AccessKey, AccessLevel> accessCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(10_000)
            .build();

    public PropertyInternalClient(AppProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.propertyServiceUri())
                .defaultHeader("X-Internal-Api-Key", properties.internalApiKey())
                .build();
    }

    public AccessLevel access(UUID propertyId, UUID userId) {
        return accessCache.get(new AccessKey(propertyId, userId), key -> {
            Map<String, Object> data = getData("/internal/properties/" + key.propertyId()
                    + "/access/" + key.userId());
            if (data == null) {
                return AccessLevel.NONE;
            }
            return AccessLevel.valueOf(String.valueOf(data.get("access")));
        });
    }

    public Optional<PropertyInfo> property(UUID propertyId) {
        Map<String, Object> data = getData("/internal/properties/" + propertyId);
        if (data == null) {
            return Optional.empty();
        }
        return Optional.of(new PropertyInfo(
                UUID.fromString(String.valueOf(data.get("id"))),
                UUID.fromString(String.valueOf(data.get("ownerUserId"))),
                String.valueOf(data.get("name")),
                String.valueOf(data.get("type")),
                String.valueOf(data.get("locality")),
                Boolean.TRUE.equals(data.get("openToOffers")),
                Boolean.TRUE.equals(data.get("deleted"))));
    }

    public Optional<AssetInfo> asset(UUID assetId) {
        Map<String, Object> data = getData("/internal/assets/" + assetId);
        if (data == null) {
            return Optional.empty();
        }
        return Optional.of(new AssetInfo(
                UUID.fromString(String.valueOf(data.get("id"))),
                UUID.fromString(String.valueOf(data.get("propertyId"))),
                String.valueOf(data.get("objectPath")),
                String.valueOf(data.get("sha256Hash")),
                decimal(data.get("gpsLat")),
                decimal(data.get("gpsLng")),
                Instant.parse(String.valueOf(data.get("capturedAt"))),
                String.valueOf(data.get("description")),
                decimal(data.get("estimatedValue")),
                String.valueOf(data.get("category"))));
    }

    /** Throws on transport failure — callers decide whether suggestions are critical. */
    @SuppressWarnings("unchecked")
    public List<AssetNear> assetsNear(UUID propertyId, BigDecimal lat, BigDecimal lng, double radiusM) {
        Map<String, Object> body = restClient.get()
                .uri(builder -> buildAssetsNearUri(builder, propertyId, lat, lng, radiusM))
                .retrieve()
                .body(Map.class);
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        return items.stream()
                .map(item -> new AssetNear(
                        UUID.fromString(String.valueOf(item.get("assetId"))),
                        decimal(item.get("distanceMeters")).doubleValue(),
                        String.valueOf(item.get("description")),
                        decimal(item.get("estimatedValue")),
                        String.valueOf(item.get("category")),
                        String.valueOf(item.get("thumbnailUrl")),
                        String.valueOf(item.get("sha256Hash")),
                        Instant.parse(String.valueOf(item.get("capturedAt")))))
                .toList();
    }

    private static java.net.URI buildAssetsNearUri(UriBuilder builder, UUID propertyId,
                                                   BigDecimal lat, BigDecimal lng, double radiusM) {
        return builder.path("/internal/properties/{id}/assets-near")
                .queryParam("lat", lat)
                .queryParam("lng", lng)
                .queryParam("radiusM", radiusM)
                .build(propertyId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getData(String uri) {
        try {
            Map<String, Object> body = restClient.get().uri(uri).retrieve().body(Map.class);
            return body == null ? null : (Map<String, Object>) body.get("data");
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    private static BigDecimal decimal(Object value) {
        return new BigDecimal(String.valueOf(value));
    }
}
