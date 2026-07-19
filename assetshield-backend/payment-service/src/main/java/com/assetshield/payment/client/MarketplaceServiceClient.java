package com.assetshield.payment.client;

import com.assetshield.payment.config.AppProperties;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Downstream dispatch for subscription settlements: tells marketplace-service a
 * subscription payment succeeded (X-Internal-Api-Key). Throws on transport
 * failure — the caller decides whether to swallow (reconciler retries later).
 */
@Component
public class MarketplaceServiceClient {

    private final RestClient restClient;

    public MarketplaceServiceClient(AppProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.marketplaceServiceUri())
                .defaultHeader("X-Internal-Api-Key", properties.internalApiKey())
                .build();
    }

    public void subscriptionPaymentConfirmed(String purpose, UUID referenceEntityId, UUID paymentId) {
        restClient.post()
                .uri("/internal/subscriptions/payment-confirmed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "purpose", purpose,
                        "referenceEntityId", referenceEntityId.toString(),
                        "paymentId", paymentId.toString()))
                .retrieve()
                .toBodilessEntity();
    }
}
