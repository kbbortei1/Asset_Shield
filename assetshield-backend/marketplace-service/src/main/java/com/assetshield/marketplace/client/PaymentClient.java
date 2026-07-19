package com.assetshield.marketplace.client;

import com.assetshield.marketplace.common.ApiException;
import com.assetshield.marketplace.common.ErrorCode;
import com.assetshield.marketplace.config.AppProperties;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Initializes subscription checkouts via payment-service's internal API. */
@Component
public class PaymentClient {

    public record PaymentInit(UUID paymentId, String reference, String authorizationUrl) {
    }

    private static final Logger log = LoggerFactory.getLogger(PaymentClient.class);

    private final RestClient restClient;

    public PaymentClient(AppProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.paymentServiceUri())
                .defaultHeader("X-Internal-Api-Key", properties.internalApiKey())
                .build();
    }

    @SuppressWarnings("unchecked")
    public PaymentInit initialize(UUID userId, String userPhone, String purpose,
                                  BigDecimal amountGhs, UUID referenceEntityId) {
        try {
            Map<String, Object> body = restClient.post()
                    .uri("/internal/payments/initialize")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "userId", userId.toString(),
                            "userPhone", userPhone,
                            "purpose", purpose,
                            "amountGhs", amountGhs,
                            "referenceEntityId", referenceEntityId.toString()))
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
            log.error("Payment initialization failed for {} {}: {}", purpose, referenceEntityId, e.getMessage());
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not start the payment");
        }
    }
}
