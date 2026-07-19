package com.assetshield.payment.web;

import com.assetshield.payment.config.AppProperties;
import com.assetshield.payment.service.PaymentSettlementService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Paystack webhook. The HMAC-SHA512 signature is verified over the EXACT raw
 * request bytes (no deserialization first), constant-time. Bad signature →
 * bare 401 with no detail for attackers. Handled outcomes always return 200 —
 * Paystack retries non-2xx, and business quirks must not trigger retries.
 */
@RestController
public class PaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);

    private final byte[] secretKey;
    private final PaymentSettlementService settlementService;
    private final ObjectMapper objectMapper;

    public PaymentWebhookController(AppProperties properties,
                                    PaymentSettlementService settlementService,
                                    ObjectMapper objectMapper) {
        String secret = properties.payments().paystackSecretKey();
        this.secretKey = (secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8);
        this.settlementService = settlementService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/v1/payments/webhook")
    public ResponseEntity<Void> webhook(@RequestBody byte[] rawBody,
                                        @RequestHeader(value = "x-paystack-signature", required = false)
                                        String signature) {
        if (signature == null || !signatureMatches(rawBody, signature)) {
            return ResponseEntity.status(401).build();
        }

        try {
            JsonNode payload = objectMapper.readTree(rawBody);
            String event = payload.path("event").asString();
            if ("charge.success".equals(event)) {
                String reference = payload.path("data").path("reference").asString();
                settlementService.settle(reference, new String(rawBody, StandardCharsets.UTF_8));
            } else {
                log.info("Webhook event '{}' ignored", event);
            }
        } catch (Exception e) {
            // malformed-but-signed payloads are acked; only genuine crashes 500
            log.error("Webhook processing failed", e);
        }
        return ResponseEntity.ok().build();
    }

    private boolean signatureMatches(byte[] rawBody, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secretKey, "HmacSHA512"));
            String expected = HexFormat.of().formatHex(mac.doFinal(rawBody));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    signature.toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("HMAC computation failed", e);
            return false;
        }
    }
}
