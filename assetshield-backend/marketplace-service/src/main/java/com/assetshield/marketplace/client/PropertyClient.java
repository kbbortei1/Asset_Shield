package com.assetshield.marketplace.client;

import com.assetshield.marketplace.config.AppProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * Internal lookups against property-service (X-Internal-Api-Key). Property
 * summaries are cached for 5 minutes (names on interest/quote lists); the
 * leads list and access checks are never cached — they gate privacy.
 */
@Component
public class PropertyClient {

    /** Mirror of property's InternalPropertyResponse. */
    public record PropertyInfo(UUID id, UUID ownerUserId, String name, String type,
                               String locality, boolean openToOffers, boolean deleted) {
    }

    /** Mirror of property's lead list item — exactly the five lead fields. */
    public record LeadItem(UUID propertyId, String ownerDisplayName, String propertyName,
                           String propertyType, String locality) {
    }

    public record LeadPage(List<LeadItem> items, int page, int size, long totalElements, int totalPages) {
    }

    private final RestClient restClient;
    private final Cache<UUID, Optional<PropertyInfo>> propertyCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(10_000)
            .build();

    public PropertyClient(AppProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.propertyServiceUri())
                .defaultHeader("X-Internal-Api-Key", properties.internalApiKey())
                .build();
    }

    /** Uncached: drives express-interest validation (existence is sensitive). */
    public Optional<PropertyInfo> property(UUID propertyId) {
        return fetchProperty(propertyId);
    }

    /** Cached 5 min: display names on lists only — never an access decision. */
    public Optional<PropertyInfo> propertyCached(UUID propertyId) {
        return propertyCache.get(propertyId, this::fetchProperty);
    }

    @SuppressWarnings("unchecked")
    private Optional<PropertyInfo> fetchProperty(UUID propertyId) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri("/internal/properties/{id}", propertyId)
                    .retrieve()
                    .body(Map.class);
            return Optional.of(toPropertyInfo((Map<String, Object>) body.get("data")));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    private static PropertyInfo toPropertyInfo(Map<String, Object> data) {
        return new PropertyInfo(
                UUID.fromString(String.valueOf(data.get("id"))),
                UUID.fromString(String.valueOf(data.get("ownerUserId"))),
                String.valueOf(data.get("name")),
                String.valueOf(data.get("type")),
                String.valueOf(data.get("locality")),
                Boolean.parseBoolean(String.valueOf(data.get("openToOffers"))),
                Boolean.parseBoolean(String.valueOf(data.get("deleted"))));
    }

    /** OWNER | MEMBER_EXPORT | MEMBER | NONE. */
    @SuppressWarnings("unchecked")
    public String access(UUID propertyId, UUID userId) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri("/internal/properties/{id}/access/{userId}", propertyId, userId)
                    .retrieve()
                    .body(Map.class);
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            return String.valueOf(data.get("access"));
        } catch (HttpClientErrorException.NotFound e) {
            return "NONE";
        }
    }

    /** Paginated pass-through of property's opted-in lead projection. */
    @SuppressWarnings("unchecked")
    public LeadPage leads(String propertyType, String locality, int page, int size) {
        Map<String, Object> body = restClient.get()
                .uri(builder -> leadUri(builder, propertyType, locality, page, size))
                .retrieve()
                .body(Map.class);
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        return new LeadPage(
                items.stream().map(item -> new LeadItem(
                        UUID.fromString(String.valueOf(item.get("propertyId"))),
                        String.valueOf(item.get("ownerDisplayName")),
                        String.valueOf(item.get("propertyName")),
                        String.valueOf(item.get("propertyType")),
                        String.valueOf(item.get("locality")))).toList(),
                ((Number) data.get("page")).intValue(),
                ((Number) data.get("size")).intValue(),
                ((Number) data.get("totalElements")).longValue(),
                ((Number) data.get("totalPages")).intValue());
    }

    private static java.net.URI leadUri(UriBuilder builder, String propertyType, String locality,
                                        int page, int size) {
        builder.path("/internal/properties/leads")
                .queryParam("page", page)
                .queryParam("size", size);
        if (propertyType != null && !propertyType.isBlank()) {
            builder.queryParam("propertyType", propertyType);
        }
        if (locality != null && !locality.isBlank()) {
            builder.queryParam("locality", locality);
        }
        return builder.build();
    }
}
