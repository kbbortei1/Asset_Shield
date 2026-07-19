package com.assetshield.payment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assetshield.payment.TestProps;
import com.assetshield.payment.TestTokens;
import com.assetshield.payment.domain.Payment;
import com.assetshield.payment.domain.PaymentStatus;
import com.assetshield.payment.client.DamageServiceClient;
import com.assetshield.payment.client.MarketplaceServiceClient;
import com.assetshield.payment.service.PaymentSettlementService;
import com.assetshield.payment.repo.PaymentRepository;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "JWT_SECRET=" + TestProps.JWT_SECRET,
        "INTERNAL_API_KEY=" + TestProps.INTERNAL_API_KEY,
        "PAYMENTS_MODE=mock",
        "MOCK_AUTO_SETTLE_MS=-1", // tests drive settlement explicitly
        "PAYSTACK_SECRET_KEY=" + PaymentFlowIT.WEBHOOK_SECRET
})
@AutoConfigureMockMvc
@Testcontainers
class PaymentFlowIT {

    static final String WEBHOOK_SECRET = "sk_test_webhook_secret_for_it";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    PaymentSettlementService settlementService;

    @MockitoBean
    DamageServiceClient damageServiceClient;

    @MockitoBean
    MarketplaceServiceClient marketplaceServiceClient;

    private record InitResult(UUID paymentId, String reference, UUID userId, UUID dossierId) {
    }

    private InitResult initializePayment() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        MvcResult result = mockMvc.perform(post("/internal/payments/initialize")
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","userPhone":"+233244000001","purpose":"DOSSIER_FEE",
                                 "amountGhs":50.00,"referenceEntityId":"%s"}
                                """.formatted(userId, dossierId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reference",
                        org.hamcrest.Matchers.startsWith("ASGH-DSR-")))
                .andExpect(jsonPath("$.data.authorizationUrl").isNotEmpty())
                .andReturn();
        var data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        return new InitResult(UUID.fromString(data.get("paymentId").asString()),
                data.get("reference").asString(), userId, dossierId);
    }

    private static String webhookBody(String reference) {
        return "{\"event\":\"charge.success\",\"data\":{\"reference\":\"" + reference
                + "\",\"amount\":5000,\"currency\":\"GHS\"}}";
    }

    private static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private MvcResult postWebhook(String body, String signature) throws Exception {
        var request = post("/api/v1/payments/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (signature != null) {
            request = request.header("x-paystack-signature", signature);
        }
        return mockMvc.perform(request).andReturn();
    }

    // ── flows ────────────────────────────────────────────────────────────────

    @Test
    void validWebhookSettlesAndReplaysAreIdempotent() throws Exception {
        InitResult init = initializePayment();
        doNothing().when(damageServiceClient).dossierPaymentConfirmed(any(), any());

        String body = webhookBody(init.reference());
        String signature = sign(body);
        for (int i = 0; i < 3; i++) {
            assertThat(postWebhook(body, signature).getResponse().getStatus()).isEqualTo(200);
        }

        Payment payment = paymentRepository.findByProviderReference(init.reference()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getWebhookReceivedAt()).isNotNull();
        assertThat(payment.getRawWebhook()).contains(init.reference());
        assertThat(payment.getDispatchedAt()).isNotNull();
        // delivered 3x → exactly one settlement, one dispatch
        verify(damageServiceClient, times(1))
                .dossierPaymentConfirmed(eq(init.dossierId()), eq(init.paymentId()));
    }

    @Test
    void tamperedBodyOrBadSignatureIs401AndWritesNothing() throws Exception {
        InitResult init = initializePayment();
        String body = webhookBody(init.reference());
        String signature = sign(body);

        // signature computed over DIFFERENT bytes than delivered
        String tampered = body.replace("5000", "1");
        assertThat(postWebhook(tampered, signature).getResponse().getStatus()).isEqualTo(401);
        // garbage signature
        assertThat(postWebhook(body, "f".repeat(128)).getResponse().getStatus()).isEqualTo(401);
        // missing signature
        assertThat(postWebhook(body, null).getResponse().getStatus()).isEqualTo(401);

        Payment payment = paymentRepository.findByProviderReference(init.reference()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.INITIATED);
        assertThat(payment.getWebhookReceivedAt()).isNull();
        verify(damageServiceClient, times(0)).dossierPaymentConfirmed(any(), any());
    }

    @Test
    void unknownReferenceIsAcknowledgedNotRetried() throws Exception {
        String body = webhookBody("ASGH-DSR-000000000000");
        assertThat(postWebhook(body, sign(body)).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void verifyEndpointIsPayerOnlyAndSettles() throws Exception {
        InitResult init = initializePayment();
        doNothing().when(damageServiceClient).dossierPaymentConfirmed(any(), any());

        // a stranger cannot even see the payment
        mockMvc.perform(post("/api/v1/payments/{ref}/verify", init.reference())
                        .header(HttpHeaders.AUTHORIZATION,
                                TestTokens.bearer(UUID.randomUUID(), "+233244999999")))
                .andExpect(status().isNotFound());

        // the payer verifies → mock provider says SUCCESS → settled + dispatched
        mockMvc.perform(post("/api/v1/payments/{ref}/verify", init.reference())
                        .header(HttpHeaders.AUTHORIZATION,
                                TestTokens.bearer(init.userId(), "+233244000001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.purpose").value("DOSSIER_FEE"));

        verify(damageServiceClient, times(1))
                .dossierPaymentConfirmed(eq(init.dossierId()), eq(init.paymentId()));

        mockMvc.perform(get("/api/v1/payments/{ref}", init.reference())
                        .header(HttpHeaders.AUTHORIZATION,
                                TestTokens.bearer(init.userId(), "+233244000001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(50.00))
                .andExpect(jsonPath("$.data.currency").value("GHS"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    void failedDispatchLeavesUndispatchedAndReconcilerRetries() throws Exception {
        InitResult init = initializePayment();
        doThrow(new RuntimeException("damage-service down"))
                .when(damageServiceClient).dossierPaymentConfirmed(any(), any());

        String body = webhookBody(init.reference());
        assertThat(postWebhook(body, sign(body)).getResponse().getStatus()).isEqualTo(200);

        Payment payment = paymentRepository.findByProviderReference(init.reference()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getDispatchedAt()).isNull();

        // downstream recovers → reconciler re-dispatches
        doNothing().when(damageServiceClient).dossierPaymentConfirmed(any(), any());
        settlementService.reconcile();

        Payment reconciled = paymentRepository.findByProviderReference(init.reference()).orElseThrow();
        assertThat(reconciled.getDispatchedAt()).isNotNull();
        verify(damageServiceClient, times(2))
                .dossierPaymentConfirmed(eq(init.dossierId()), eq(init.paymentId()));
    }
}
