package com.assetshield.marketplace.client;

import com.assetshield.marketplace.config.AppProperties;
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
 * Internal dossier lookups against damage-service (X-Internal-Api-Key).
 * Meta is cached for 60 seconds (shared-dossier lists); verify is never
 * cached — integrity must be recomputed on every call.
 */
@Component
public class DossierClient {

    /** Mirror of damage's internal MetaResponse. */
    public record DossierMeta(UUID id, UUID requestedByUserId, UUID propertyId, UUID damageReportId,
                              String status, String manifestHash, String fileUrl,
                              BigDecimal totalEstimatedLoss, String disasterType, String generatedAt) {
    }

    public record Mismatch(String objectPath, String expected, String actual) {
    }

    /** Mirror of damage's internal VerifyResponse. */
    public record DossierVerify(String manifestHash, String recomputedHash, boolean tamperEvident,
                                int photoCount, List<Mismatch> mismatches) {
    }

    private final RestClient restClient;
    private final Cache<UUID, Optional<DossierMeta>> metaCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(10_000)
            .build();

    public DossierClient(AppProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.damageServiceUri())
                .defaultHeader("X-Internal-Api-Key", properties.internalApiKey())
                .build();
    }

    /** Uncached: drives share validation (status must be current). */
    public Optional<DossierMeta> meta(UUID dossierId) {
        return fetchMeta(dossierId);
    }

    /** Cached 60 s: display fields on shared-dossier lists only. */
    public Optional<DossierMeta> metaCached(UUID dossierId) {
        return metaCache.get(dossierId, this::fetchMeta);
    }

    @SuppressWarnings("unchecked")
    private Optional<DossierMeta> fetchMeta(UUID dossierId) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri("/internal/dossiers/{id}/meta", dossierId)
                    .retrieve()
                    .body(Map.class);
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            return Optional.of(new DossierMeta(
                    UUID.fromString(String.valueOf(data.get("id"))),
                    UUID.fromString(String.valueOf(data.get("requestedByUserId"))),
                    UUID.fromString(String.valueOf(data.get("propertyId"))),
                    UUID.fromString(String.valueOf(data.get("damageReportId"))),
                    String.valueOf(data.get("status")),
                    data.get("manifestHash") == null ? null : String.valueOf(data.get("manifestHash")),
                    data.get("fileUrl") == null ? null : String.valueOf(data.get("fileUrl")),
                    data.get("totalEstimatedLoss") == null
                            ? null : new BigDecimal(String.valueOf(data.get("totalEstimatedLoss"))),
                    data.get("disasterType") == null ? null : String.valueOf(data.get("disasterType")),
                    data.get("generatedAt") == null ? null : String.valueOf(data.get("generatedAt"))));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    public Optional<DossierVerify> verify(UUID dossierId) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri("/internal/dossiers/{id}/verify", dossierId)
                    .retrieve()
                    .body(Map.class);
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            List<Map<String, Object>> rawMismatches =
                    (List<Map<String, Object>>) data.getOrDefault("mismatches", List.of());
            return Optional.of(new DossierVerify(
                    String.valueOf(data.get("manifestHash")),
                    String.valueOf(data.get("recomputedHash")),
                    Boolean.parseBoolean(String.valueOf(data.get("tamperEvident"))),
                    ((Number) data.get("photoCount")).intValue(),
                    rawMismatches.stream().map(m -> new Mismatch(
                            String.valueOf(m.get("objectPath")),
                            String.valueOf(m.get("expected")),
                            String.valueOf(m.get("actual")))).toList()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
