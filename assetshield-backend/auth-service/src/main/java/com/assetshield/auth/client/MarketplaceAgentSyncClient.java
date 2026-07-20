package com.assetshield.auth.client;

import com.assetshield.auth.config.AppProperties;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Pushes verified-agent details to marketplace's idempotent
 * POST /internal/agents/sync (X-Internal-Api-Key). Callers decide what each
 * outcome means; this client never throws on the expected 409.
 */
@Component
public class MarketplaceAgentSyncClient {

    public enum SyncResult {
        /** 2xx — marketplace accepted (or already had) the agent. */
        SYNCED,
        /** 409 — licence registered to a different user; admin resolves manually. */
        LICENCE_CONFLICT
    }

    private final RestClient restClient;

    public MarketplaceAgentSyncClient(AppProperties properties) {
        this.restClient = RestClient.builder()
                .requestFactory(InternalHttp.requestFactory())
                .baseUrl(properties.marketplaceServiceUri())
                .defaultHeader("X-Internal-Api-Key", properties.internalApiKey())
                .build();
    }

    /** Throws on transport/5xx failures — the re-push job retries those. */
    public SyncResult sync(UUID userId, String insurerName, String nicLicenceNo) {
        try {
            restClient.post()
                    .uri("/internal/agents/sync")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("userId", userId.toString(),
                            "insurerName", insurerName,
                            "nicLicenceNo", nicLicenceNo))
                    .retrieve()
                    .toBodilessEntity();
            return SyncResult.SYNCED;
        } catch (HttpClientErrorException.Conflict e) {
            return SyncResult.LICENCE_CONFLICT;
        }
    }
}
