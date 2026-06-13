package com.assetshield.marketplace.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assetshield.marketplace.TestTokens;
import com.assetshield.marketplace.client.PropertyClient;
import com.assetshield.marketplace.domain.AgentSubscription;
import com.assetshield.marketplace.domain.InsuranceAgent;
import com.assetshield.marketplace.domain.SubscriptionStatus;
import com.assetshield.marketplace.domain.VerificationStatus;
import com.assetshield.marketplace.subscription.SubscriptionSettlementService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** P0 projection strictness and the two access gates on /agents/me/leads. */
class LeadProjectionIT extends MarketplaceITBase {

    @Autowired
    SubscriptionSettlementService settlementService;

    private static String agentBearer(InsuranceAgent agent) {
        return "Bearer " + TestTokens.token(agent.getUserId(), "AGENT", "+233244111111");
    }

    @Test
    void leadItemsSerializeExactlyTheFiveLeadFields() throws Exception {
        InsuranceAgent agent = subscribedAgent();
        UUID propertyId = UUID.randomUUID();
        when(propertyClient.leads(any(), any(), anyInt(), anyInt()))
                .thenReturn(new PropertyClient.LeadPage(
                        List.of(new PropertyClient.LeadItem(propertyId, "Ama O.", "Adabraka Lodge",
                                "RESIDENTIAL", "Adabraka")),
                        0, 20, 1, 1));

        MvcResult result = mockMvc.perform(get("/api/v1/agents/me/leads")
                        .header(HttpHeaders.AUTHORIZATION, agentBearer(agent)))
                .andExpect(status().isOk())
                .andReturn();

        // assert on the raw serialized JSON, not the DTO: the key set must be
        // EXACTLY the five lead fields — nothing else may ever leak to agents
        JsonNode item = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("items").get(0);
        assertThat(item.propertyNames()).containsExactlyInAnyOrder(
                "propertyId", "ownerDisplayName", "propertyName", "propertyType", "locality");
        assertThat(item.get("propertyId").asString()).isEqualTo(propertyId.toString());
    }

    @Test
    void unverifiedAgentIsBlockedWithAgentNotVerified() throws Exception {
        InsuranceAgent agent = newAgent(VerificationStatus.PENDING_VERIFICATION);
        mockMvc.perform(get("/api/v1/agents/me/leads")
                        .header(HttpHeaders.AUTHORIZATION, agentBearer(agent)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.errorCode").value("AGENT_NOT_VERIFIED"));
    }

    @Test
    void verifiedAgentWithoutSubscriptionIsBlockedWithSubscriptionInactive() throws Exception {
        InsuranceAgent agent = newAgent(VerificationStatus.VERIFIED);
        mockMvc.perform(get("/api/v1/agents/me/leads")
                        .header(HttpHeaders.AUTHORIZATION, agentBearer(agent)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.errorCode").value("SUBSCRIPTION_INACTIVE"));
    }

    @Test
    void subscriptionLapseClosesTheGateAgainAfterTheSweep() throws Exception {
        InsuranceAgent agent = newAgent(VerificationStatus.VERIFIED);
        AgentSubscription sub = activateSubscription(agent);
        when(propertyClient.leads(any(), any(), anyInt(), anyInt()))
                .thenReturn(new PropertyClient.LeadPage(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/agents/me/leads")
                        .header(HttpHeaders.AUTHORIZATION, agentBearer(agent)))
                .andExpect(status().isOk());

        sub.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));
        agentSubscriptionRepository.save(sub);
        settlementService.expireLapsed();

        assertThat(agentSubscriptionRepository.findById(sub.getId()).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.EXPIRED);
        mockMvc.perform(get("/api/v1/agents/me/leads")
                        .header(HttpHeaders.AUTHORIZATION, agentBearer(agent)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.errorCode").value("SUBSCRIPTION_INACTIVE"));
    }

    @Test
    void nonAgentRoleIsBlockedFromLeads() throws Exception {
        mockMvc.perform(get("/api/v1/agents/me/leads")
                        .header(HttpHeaders.AUTHORIZATION,
                                TestTokens.bearer(UUID.randomUUID(), "+233244000009")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.errorCode").value("AGENT_NOT_VERIFIED"));
    }
}
