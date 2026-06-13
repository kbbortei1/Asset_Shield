package com.assetshield.marketplace.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assetshield.marketplace.TestProps;
import com.assetshield.marketplace.TestTokens;
import com.assetshield.marketplace.domain.AgentInterest;
import com.assetshield.marketplace.domain.InsuranceAgent;
import com.assetshield.marketplace.domain.InterestStatus;
import com.assetshield.marketplace.repo.AgentInterestRepository;
import com.assetshield.marketplace.repo.DossierShareRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class InterestLifecycleIT extends MarketplaceITBase {

    @Autowired
    AgentInterestRepository interestRepository;

    @Autowired
    DossierShareRepository shareRepository;

    private static String agentBearer(InsuranceAgent agent) {
        return "Bearer " + TestTokens.token(agent.getUserId(), "AGENT", "+233244111111");
    }

    private static String ownerBearer(UUID ownerId) {
        return TestTokens.bearer(ownerId, "+233200000001");
    }

    private UUID expressInterest(InsuranceAgent agent, UUID propertyId, UUID ownerId) throws Exception {
        when(propertyClient.property(eq(propertyId)))
                .thenReturn(Optional.of(optedInProperty(propertyId, ownerId)));
        MvcResult result = mockMvc.perform(post("/api/v1/leads/{id}/express-interest", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, agentBearer(agent)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("interestId").asString());
    }

    private void respond(UUID ownerId, UUID interestId, boolean accept, int expectedStatus) throws Exception {
        mockMvc.perform(put("/api/v1/agent-interests/{id}/respond", interestId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accept\":" + accept + "}"))
                .andExpect(status().is(expectedStatus));
    }

    // ── privacy 404s ─────────────────────────────────────────────────────────

    @Test
    void unknownAndNonOptedInPropertiesAre404WithIdenticalBodies() throws Exception {
        InsuranceAgent agent = subscribedAgent();
        UUID unknown = UUID.randomUUID();
        UUID notOptedIn = UUID.randomUUID();
        when(propertyClient.property(eq(unknown))).thenReturn(Optional.empty());
        when(propertyClient.property(eq(notOptedIn)))
                .thenReturn(Optional.of(new com.assetshield.marketplace.client.PropertyClient.PropertyInfo(
                        notOptedIn, UUID.randomUUID(), "Hidden House", "RESIDENTIAL",
                        "Labone", false, false)));

        String unknownBody = mockMvc.perform(post("/api/v1/leads/{id}/express-interest", unknown)
                        .header(HttpHeaders.AUTHORIZATION, agentBearer(agent)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String notOptedInBody = mockMvc.perform(post("/api/v1/leads/{id}/express-interest", notOptedIn)
                        .header(HttpHeaders.AUTHORIZATION, agentBearer(agent)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // the response must not reveal whether the property exists
        assertThat(unknownBody).isEqualTo(notOptedInBody);
    }

    // ── lifecycle ────────────────────────────────────────────────────────────

    @Test
    void duplicatePendingInterestIsRejectedAndReExpressAfterDeclineIsAllowed() throws Exception {
        InsuranceAgent agent = subscribedAgent();
        UUID propertyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID interestId = expressInterest(agent, propertyId, ownerId);

        mockMvc.perform(post("/api/v1/leads/{id}/express-interest", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, agentBearer(agent)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.errorCode").value("DUPLICATE_PENDING_INTEREST"));

        respond(ownerId, interestId, false, 200);
        // the partial unique index only guards live PENDING rows
        UUID secondId = expressInterest(agent, propertyId, ownerId);
        assertThat(secondId).isNotEqualTo(interestId);
    }

    @Test
    void ownerAcceptRevealsOwnerNameToTheAgentAndDoubleRespondConflicts() throws Exception {
        InsuranceAgent agent = subscribedAgent();
        UUID propertyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID interestId = expressInterest(agent, propertyId, ownerId);
        when(propertyClient.propertyCached(eq(propertyId)))
                .thenReturn(Optional.of(optedInProperty(propertyId, ownerId)));
        when(authUserClient.byId(eq(ownerId))).thenReturn(userInfo(ownerId, "Ama Owusu"));
        when(authUserClient.byId(eq(agent.getUserId())))
                .thenReturn(userInfo(agent.getUserId(), "Kojo Agent"));

        // before acceptance the agent list carries no owner name
        mockMvc.perform(get("/api/v1/agents/me/interests")
                        .header(HttpHeaders.AUTHORIZATION, agentBearer(agent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.items[0].ownerFullName").doesNotExist());

        // a stranger cannot respond — 404, not 403
        respond(UUID.randomUUID(), interestId, true, 404);
        respond(ownerId, interestId, true, 200);

        mockMvc.perform(get("/api/v1/agents/me/interests")
                        .header(HttpHeaders.AUTHORIZATION, agentBearer(agent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.items[0].ownerFullName").value("Ama Owusu"));

        // owner list shows the licence only once ACCEPTED
        mockMvc.perform(get("/api/v1/users/me/agent-interests")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].nicLicenceNo").value(agent.getNicLicenceNo()));

        mockMvc.perform(put("/api/v1/agent-interests/{id}/respond", interestId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accept\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.errorCode").value("ALREADY_RESPONDED"));
    }

    /** FR28 in one arc: accept → share → revoke → every agent read goes dark. */
    @Test
    void revokingTheConnectionCascadesToSharesAndAgentReadsBecome404() throws Exception {
        InsuranceAgent agent = subscribedAgent();
        UUID propertyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        UUID interestId = expressInterest(agent, propertyId, ownerId);
        respond(ownerId, interestId, true, 200);

        when(dossierClient.meta(eq(dossierId)))
                .thenReturn(Optional.of(readyDossier(dossierId, propertyId, ownerId)));
        when(propertyClient.access(eq(propertyId), eq(ownerId))).thenReturn("OWNER");
        mockMvc.perform(post("/api/v1/dossiers/{id}/share-to-agent", dossierId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentInterestId\":\"" + interestId + "\"}"))
                .andExpect(status().isCreated());

        // agent sees the share while consent is live
        when(dossierClient.metaCached(eq(dossierId)))
                .thenReturn(Optional.of(readyDossier(dossierId, propertyId, ownerId)));
        when(propertyClient.propertyCached(eq(propertyId)))
                .thenReturn(Optional.of(optedInProperty(propertyId, ownerId)));
        when(authUserClient.byId(any(UUID.class))).thenReturn(userInfo(ownerId, "Ama Owusu"));
        mockMvc.perform(get("/api/v1/agents/me/shared-dossiers")
                        .header(HttpHeaders.AUTHORIZATION, agentBearer(agent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].dossierId").value(dossierId.toString()));

        mockMvc.perform(delete("/api/v1/agent-interests/{id}", interestId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));

        // the cascade set revoked_at on the share in the same transaction
        assertThat(shareRepository.findByDossierIdAndAgentIdAndRevokedAtIsNull(
                dossierId, agent.getId())).isEmpty();
        mockMvc.perform(get("/api/v1/agents/me/shared-dossiers")
                        .header(HttpHeaders.AUTHORIZATION, agentBearer(agent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
        mockMvc.perform(get("/api/v1/dossiers/{id}/verify", dossierId)
                        .header(HttpHeaders.AUTHORIZATION, agentBearer(agent)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/dossiers/{id}/quote", dossierId)
                        .header(HttpHeaders.AUTHORIZATION, agentBearer(agent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverageAmount\":10000,\"premium\":120,\"termMonths\":12}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void revokingANonAcceptedInterestConflicts() throws Exception {
        InsuranceAgent agent = subscribedAgent();
        UUID propertyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID interestId = expressInterest(agent, propertyId, ownerId);
        mockMvc.perform(delete("/api/v1/agent-interests/{id}", interestId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer(ownerId)))
                .andExpect(status().isConflict());
    }

    // ── opt-out ──────────────────────────────────────────────────────────────

    @Test
    void optOutDeclinesPendingInterestsButLeavesAcceptedConnections() throws Exception {
        UUID propertyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        InsuranceAgent acceptedAgent = subscribedAgent();
        InsuranceAgent pendingAgent = subscribedAgent();
        UUID acceptedId = expressInterest(acceptedAgent, propertyId, ownerId);
        respond(ownerId, acceptedId, true, 200);
        UUID pendingId = expressInterest(pendingAgent, propertyId, ownerId);

        mockMvc.perform(post("/internal/marketplace/optin-changed")
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"propertyId\":\"" + propertyId + "\",\"openToOffers\":false}"))
                .andExpect(status().isOk());

        AgentInterest pending = interestRepository.findById(pendingId).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(InterestStatus.DECLINED);
        assertThat(pending.getRespondedAt()).isNotNull();
        assertThat(interestRepository.findById(acceptedId).orElseThrow().getStatus())
                .isEqualTo(InterestStatus.ACCEPTED);
    }
}
