package com.assetshield.notification.client;

import com.assetshield.notification.config.AppProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Internal lookups against property-service (X-Internal-Api-Key). Access
 * checks are cached for 60 seconds; the tips context is always fresh — it
 * drives generation, not reads.
 */
@Component
public class PropertyClient {

    public record CategoryLine(String category, long count, BigDecimal value) {
    }

    /** The rule-engine input shape. */
    public record TipsContext(UUID propertyId, UUID ownerUserId, String propertyType,
                              BigDecimal gpsLat, BigDecimal gpsLng, List<CategoryLine> byCategory) {
    }

    public record StaleProperty(UUID propertyId, UUID ownerUserId, String name,
                                String lastDocumentedAt) {
    }

    public record StalePage(List<StaleProperty> items, int page, int size,
                            long totalElements, int totalPages) {
    }

    private record AccessKey(UUID propertyId, UUID userId) {
    }

    private final RestClient restClient;
    private final Cache<AccessKey, String> accessCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(10_000)
            .build();

    public PropertyClient(AppProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.propertyServiceUri())
                .defaultHeader("X-Internal-Api-Key", properties.internalApiKey())
                .build();
    }

    @SuppressWarnings("unchecked")
    public Optional<TipsContext> tipsContext(UUID propertyId) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri("/internal/properties/{id}/tips-context", propertyId)
                    .retrieve()
                    .body(Map.class);
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            List<Map<String, Object>> lines =
                    (List<Map<String, Object>>) data.getOrDefault("byCategory", List.of());
            return Optional.of(new TipsContext(
                    UUID.fromString(String.valueOf(data.get("propertyId"))),
                    UUID.fromString(String.valueOf(data.get("ownerUserId"))),
                    String.valueOf(data.get("propertyType")),
                    decimal(data.get("gpsLat")),
                    decimal(data.get("gpsLng")),
                    lines.stream().map(line -> new CategoryLine(
                            String.valueOf(line.get("category")),
                            ((Number) line.get("count")).longValue(),
                            decimal(line.get("value")))).toList()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    /** OWNER | MEMBER_EXPORT | MEMBER | NONE, cached 60 s. */
    public String access(UUID propertyId, UUID userId) {
        return accessCache.get(new AccessKey(propertyId, userId), key -> fetchAccess(propertyId, userId));
    }

    @SuppressWarnings("unchecked")
    private String fetchAccess(UUID propertyId, UUID userId) {
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

    @SuppressWarnings("unchecked")
    public StalePage staleDocumentation(int days, int page, int size) {
        Map<String, Object> body = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/internal/properties/stale-documentation")
                        .queryParam("days", days)
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build())
                .retrieve()
                .body(Map.class);
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        return new StalePage(
                items.stream().map(item -> new StaleProperty(
                        UUID.fromString(String.valueOf(item.get("propertyId"))),
                        UUID.fromString(String.valueOf(item.get("ownerUserId"))),
                        String.valueOf(item.get("name")),
                        item.get("lastDocumentedAt") == null
                                ? null : String.valueOf(item.get("lastDocumentedAt")))).toList(),
                ((Number) data.get("page")).intValue(),
                ((Number) data.get("size")).intValue(),
                ((Number) data.get("totalElements")).longValue(),
                ((Number) data.get("totalPages")).intValue());
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }
}
