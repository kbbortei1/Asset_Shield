package com.assetshield.payment.client;

import com.assetshield.payment.config.AppProperties;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Downstream dispatch for DOSSIER_FEE settlements: tells damage-service the
 * dossier is paid (X-Internal-Api-Key). Throws on transport failure — the
 * caller decides whether to swallow (reconciler retries later).
 */
@Component
public class DamageServiceClient {

    private final RestClient restClient;

    public DamageServiceClient(AppProperties properties) {
        this.restClient = RestClient.builder()
                .requestFactory(InternalHttp.requestFactory())
                .baseUrl(properties.damageServiceUri())
                .defaultHeader("X-Internal-Api-Key", properties.internalApiKey())
                .build();
    }

    public void dossierPaymentConfirmed(UUID dossierId, UUID paymentId) {
        restClient.post()
                .uri("/internal/dossiers/{id}/payment-confirmed", dossierId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("paymentId", paymentId.toString()))
                .retrieve()
                .toBodilessEntity();
    }
}
