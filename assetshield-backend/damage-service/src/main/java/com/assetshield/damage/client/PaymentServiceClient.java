package com.assetshield.damage.client;

import com.assetshield.damage.common.ApiException;
import com.assetshield.damage.common.ErrorCode;
import com.assetshield.damage.config.AppProperties;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Initializes dossier-fee checkouts via payment-service's internal API. */
@Component
public class PaymentServiceClient {

    public record PaymentInit(UUID paymentId, String reference, String authorizationUrl) {
    }

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceClient.class);

    private final RestClient restClient;

    public PaymentServiceClient(AppProperties properties) {
        this.restClient = RestClient.builder()
                .requestFactory(InternalHttp.requestFactory())
                .baseUrl(properties.paymentServiceUri())
                .defaultHeader("X-Internal-Api-Key", properties.internalApiKey())
                .build();
    }

    @SuppressWarnings("unchecked")
    public PaymentInit initializeDossierFee(UUID userId, String userPhone, BigDecimal amountGhs,
                                            UUID dossierId) {
        try {
            Map<String, Object> body = restClient.post()
                    .uri("/internal/payments/initialize")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "userId", userId.toString(),
                            "userPhone", userPhone,
                            "purpose", "DOSSIER_FEE",
                            "amountGhs", amountGhs,
                            "referenceEntityId", dossierId.toString()))
                    .retrieve()
                    .body(Map.class);
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            return new PaymentInit(
                    UUID.fromString(String.valueOf(data.get("paymentId"))),
                    String.valueOf(data.get("reference")),
                    String.valueOf(data.get("authorizationUrl")));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Payment initialization failed for dossier {}: {}", dossierId, e.getMessage());
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not start the dossier payment");
        }
    }
}
