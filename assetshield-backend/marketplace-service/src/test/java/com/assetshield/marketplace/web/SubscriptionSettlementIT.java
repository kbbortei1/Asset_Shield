package com.assetshield.marketplace.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assetshield.marketplace.TestProps;
import com.assetshield.marketplace.TestTokens;
import com.assetshield.marketplace.domain.AgentSubscription;
import com.assetshield.marketplace.domain.InsuranceAgent;
import com.assetshield.marketplace.domain.SubscriptionStatus;
import com.assetshield.marketplace.domain.UserSubscription;
import com.assetshield.marketplace.domain.VerificationStatus;
import com.assetshield.marketplace.payment.PaymentSettlementService;
import com.assetshield.marketplace.repo.UserSubscriptionRepository;
import com.assetshield.marketplace.subscription.SubscriptionSettlementService;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

class SubscriptionSettlementIT extends MarketplaceITBase {

    @Autowired
    PaymentSettlementService paymentSettlementService;

    @Autowired
    SubscriptionSettlementService subscriptionSettlementService;

    @Autowired
    UserSubscriptionRepository userSubscriptionRepository;

    private static String agentBearer(InsuranceAgent agent) {
        return "Bearer " + TestTokens.token(agent.getUserId(), "AGENT", "+233244111111");
    }

    private String initAgentSubscription(InsuranceAgent agent) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/agents/me/subscription")
                        .header(HttpHeaders.AUTHORIZATION, agentBearer(agent)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(100.00))
                .andExpect(jsonPath("$.data.currency").value("GHS"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("reference").asString();
    }

    private void settle(String reference) {
        paymentSettlementService.settle(reference,
                "{\"event\":\"charge.success\",\"reference\":\"" + reference + "\"}");
    }

    @Test
    void agentSettlementActivatesExtendsAndIgnoresReplays() throws Exception {
        InsuranceAgent agent = newAgent(VerificationStatus.VERIFIED);

        // first payment → fresh ACTIVE row, now + 30 days
        String first = initAgentSubscription(agent);
        settle(first);
        AgentSubscription sub = agentSubscriptionRepository
                .findByAgentIdAndStatus(agent.getId(), SubscriptionStatus.ACTIVE).orElseThrow();
        assertThat(sub.getExpiresAt())
                .isCloseTo(Instant.now().plus(Duration.ofDays(30)), within(1, ChronoUnit.MINUTES));

        // replayed settlement of the same payment is a no-op
        Instant firstExpiry = sub.getExpiresAt();
        settle(first);
        assertThat(agentSubscriptionRepository.findById(sub.getId()).orElseThrow().getExpiresAt())
                .isEqualTo(firstExpiry);

        // renewal extends from the CURRENT expiry, never truncates
        String second = initAgentSubscription(agent);
        settle(second);
        AgentSubscription renewed = agentSubscriptionRepository
                .findById(sub.getId()).orElseThrow();
        assertThat(renewed.getExpiresAt()).isEqualTo(firstExpiry.plus(Duration.ofDays(30)));

        mockMvc.perform(get("/api/v1/agents/me/subscription")
                        .header(HttpHeaders.AUTHORIZATION, agentBearer(agent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.plan").value("MONTHLY"));
    }

    @Test
    void subscriptionEndpointReportsNoneBeforeAnyPayment() throws Exception {
        InsuranceAgent agent = newAgent(VerificationStatus.VERIFIED);
        mockMvc.perform(get("/api/v1/agents/me/subscription")
                        .header(HttpHeaders.AUTHORIZATION, agentBearer(agent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NONE"));
    }

    @Test
    void proSettlementFlipsTheTierAndTheSweepFlipsItBack() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String ownerBearer = TestTokens.bearer(ownerId, "+233200000007");

        // FREE before payment — limits visible
        mockMvc.perform(get("/api/v1/users/me/subscription")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("FREE"))
                .andExpect(jsonPath("$.data.limits.maxProperties").value(1))
                .andExpect(jsonPath("$.data.limits.maxPhotosPerProperty").value(30));
        mockMvc.perform(get("/internal/users/{id}/tier", ownerId)
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY))
                .andExpect(jsonPath("$.data.tier").value("FREE"));

        MvcResult result = mockMvc.perform(post("/api/v1/subscriptions/pro")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(15.00))
                .andReturn();
        String reference = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("reference").asString();
        settle(reference);

        mockMvc.perform(get("/internal/users/{id}/tier", ownerId)
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY))
                .andExpect(jsonPath("$.data.tier").value("PRO"));
        mockMvc.perform(get("/api/v1/users/me/subscription")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("PRO"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.expiresAt").exists());

        // PRO replay + renewal semantics match the agent flow
        UserSubscription sub = userSubscriptionRepository
                .findByUserIdAndStatus(ownerId, SubscriptionStatus.ACTIVE).orElseThrow();
        Instant firstExpiry = sub.getExpiresAt();
        settle(reference);
        assertThat(userSubscriptionRepository.findById(sub.getId()).orElseThrow().getExpiresAt())
                .isEqualTo(firstExpiry);

        // lapse + sweep → FREE again
        sub.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));
        userSubscriptionRepository.save(sub);
        subscriptionSettlementService.expireLapsed();
        mockMvc.perform(get("/internal/users/{id}/tier", ownerId)
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY))
                .andExpect(jsonPath("$.data.tier").value("FREE"));
    }

    @Test
    void agentRoleCannotBuyProSubscriptions() throws Exception {
        InsuranceAgent agent = newAgent(VerificationStatus.VERIFIED);
        mockMvc.perform(post("/api/v1/subscriptions/pro")
                        .header(HttpHeaders.AUTHORIZATION, agentBearer(agent)))
                .andExpect(status().isForbidden());
    }
}
