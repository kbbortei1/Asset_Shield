package com.assetshield.payment.provider;

import com.assetshield.payment.common.ApiException;
import com.assetshield.payment.common.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * PAYMENTS_MODE=paystack: real checkout against api.paystack.co. Amounts are
 * sent in pesewas (GHS x 100, integer); channels mobile_money + card.
 */
public class PaystackProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(PaystackProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;

    public PaystackProvider(String baseUrl, String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("PAYMENTS_MODE=paystack but PAYSTACK_SECRET_KEY is empty");
        }
        this.restClient = RestClient.builder()
                .requestFactory(com.assetshield.payment.client.InternalHttp.externalRequestFactory())
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + secretKey)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public InitResult initialize(String reference, BigDecimal amountGhs, String email,
                                 Map<String, Object> metadata) {
        try {
            Map<String, Object> body = restClient.post()
                    .uri("/transaction/initialize")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "amount", PaymentProvider.toPesewas(amountGhs),
                            "currency", "GHS",
                            "reference", reference,
                            "email", email,
                            "metadata", metadata,
                            "channels", List.of("mobile_money", "card")))
                    .retrieve()
                    .body(Map.class);
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            return new InitResult(String.valueOf(data.get("authorization_url")),
                    String.valueOf(data.get("access_code")));
        } catch (Exception e) {
            log.error("Paystack initialize failed for {}: {}", reference, e.getMessage());
            throw new ApiException(ErrorCode.PAYMENT_INIT_FAILED,
                    "Payment provider could not start the checkout");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public VerifyResult verify(String reference) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri("/transaction/verify/{reference}", reference)
                    .retrieve()
                    .body(Map.class);
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            String status = String.valueOf(data.get("status"));
            VerifyStatus verifyStatus = switch (status) {
                case "success" -> VerifyStatus.SUCCESS;
                case "failed", "abandoned", "reversed" -> VerifyStatus.FAILED;
                default -> VerifyStatus.PENDING;
            };
            // Must be real JSON: raw_webhook is a JSONB column, and Map.toString()
            // ({status=true, data={...}}) makes Postgres reject the whole settle
            // transaction — which silently left paid dossiers as INITIATED.
            return new VerifyResult(verifyStatus, toJson(body));
        } catch (Exception e) {
            log.warn("Paystack verify failed for {}: {}", reference, e.getMessage());
            return new VerifyResult(VerifyStatus.PENDING, null);
        }
    }

    /** Serialize the provider response for the JSONB audit column. */
    private static String toJson(Map<String, Object> body) {
        try {
            return MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            // Never let an audit-trail serialization problem block a settlement.
            log.warn("Could not serialize Paystack response: {}", e.getMessage());
            return null;
        }
    }
}
