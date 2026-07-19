package com.assetshield.marketplace.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assetshield.marketplace.TestProps;
import com.assetshield.marketplace.TestTokens;
import com.assetshield.marketplace.client.AuthUserClient;
import com.assetshield.marketplace.client.DossierClient;
import com.assetshield.marketplace.client.PaymentClient;
import com.assetshield.marketplace.client.PropertyClient;
import com.assetshield.marketplace.domain.InsuranceAgent;
import com.assetshield.marketplace.domain.SubscriptionStatus;
import com.assetshield.marketplace.domain.VerificationStatus;
import com.assetshield.marketplace.repo.AgentSubscriptionRepository;
import com.assetshield.marketplace.repo.InsuranceAgentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
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

/**
 * Day 6 resilience flip: with NOTIFICATIONS_MODE=remote and a DEAD
 * notification-service, settlement and interest-respond still succeed —
 * the remote client swallows transport failures (WARN only).
 */
@SpringBootTest(properties = {
        "JWT_SECRET=" + TestProps.JWT_SECRET,
        "INTERNAL_API_KEY=" + TestProps.INTERNAL_API_KEY,
        "NOTIFICATIONS_MODE=remote",
        // nothing listens here — every notify call fails at transport
        "NOTIFICATION_SERVICE_URL=http://localhost:1"
})
@AutoConfigureMockMvc
@Testcontainers
class NotificationResilienceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    InsuranceAgentRepository agentRepository;

    @Autowired
    AgentSubscriptionRepository agentSubscriptionRepository;

    @MockitoBean
    PropertyClient propertyClient;

    @MockitoBean
    DossierClient dossierClient;

    @MockitoBean
    AuthUserClient authUserClient;

    @MockitoBean
    PaymentClient paymentClient;

    @Test
    void interestRespondAndSettlementSucceedWithNotificationServiceDown() throws Exception {
        // subscribed agent
        InsuranceAgent agent = new InsuranceAgent();
        agent.setUserId(UUID.randomUUID());
        agent.setInsurerName("Star Assurance");
        agent.setNicLicenceNo("NIC-RES-" + UUID.randomUUID());
        agent.setVerificationStatus(VerificationStatus.VERIFIED);
        agent = agentRepository.save(agent);
        var sub = new com.assetshield.marketplace.domain.AgentSubscription();
        sub.setAgentId(agent.getId());
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStartedAt(Instant.now());
        sub.setExpiresAt(Instant.now().plus(Duration.ofDays(30)));
        agentSubscriptionRepository.save(sub);

        // express interest (notifies the owner at the dead URL) → 201
        UUID propertyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(propertyClient.property(eq(propertyId)))
                .thenReturn(Optional.of(new PropertyClient.PropertyInfo(propertyId, ownerId,
                        "Adabraka Lodge", "RESIDENTIAL", "Adabraka", true, false)));
        MvcResult result = mockMvc.perform(post("/api/v1/leads/{id}/express-interest", propertyId)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestTokens.token(agent.getUserId(), "AGENT", "+233244111111")))
                .andExpect(status().isCreated())
                .andReturn();
        UUID interestId = UUID.fromString(objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("data").get("interestId").asString());

        // owner responds (notifies the agent at the dead URL) → 200
        mockMvc.perform(put("/api/v1/agent-interests/{id}/respond", interestId)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(ownerId, "+233200000001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accept\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        // settlement callback (sweep notifications also ride the dead client) → applied
        mockMvc.perform(post("/internal/subscriptions/payment-confirmed")
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"purpose":"AGENT_SUBSCRIPTION","referenceEntityId":"%s","paymentId":"%s"}
                                """.formatted(agent.getId(), UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applied").value(true));
        assertThat(agentSubscriptionRepository
                .findByAgentIdAndStatus(agent.getId(), SubscriptionStatus.ACTIVE)).isPresent();
    }
}
