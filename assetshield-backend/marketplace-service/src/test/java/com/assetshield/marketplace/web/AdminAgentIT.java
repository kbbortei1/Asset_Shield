package com.assetshield.marketplace.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assetshield.marketplace.TestProps;
import com.assetshield.marketplace.TestTokens;
import com.assetshield.marketplace.domain.InsuranceAgent;
import com.assetshield.marketplace.domain.VerificationStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AdminAgentIT extends MarketplaceITBase {

    private static String adminBearer() {
        return "Bearer " + TestTokens.token(UUID.randomUUID(), "ADMIN", "+233200000099");
    }

    @Test
    void syncIsIdempotentAndLicenceCollisionsConflict() throws Exception {
        UUID userId = UUID.randomUUID();
        String licence = "NIC-SYNC-" + UUID.randomUUID();
        String body = """
                {"userId":"%s","insurerName":"Enterprise Insurance","nicLicenceNo":"%s"}
                """.formatted(userId, licence);

        mockMvc.perform(post("/internal/agents/sync")
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("PENDING_VERIFICATION"));
        // same user replayed → no-op 200
        mockMvc.perform(post("/internal/agents/sync")
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        // same licence, different user → 409 for the auth-side job to log
        mockMvc.perform(post("/internal/agents/sync")
                        .header("X-Internal-Api-Key", TestProps.INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","insurerName":"Enterprise Insurance","nicLicenceNo":"%s"}
                                """.formatted(UUID.randomUUID(), licence)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.errorCode").value("LICENCE_EXISTS"));
    }

    @Test
    void adminListsPendingAgentsAndDecidesExactlyOnce() throws Exception {
        InsuranceAgent agent = newAgent(VerificationStatus.PENDING_VERIFICATION);
        when(authUserClient.byId(any(UUID.class)))
                .thenReturn(userInfo(agent.getUserId(), "Kojo Agent"));

        mockMvc.perform(get("/api/v1/admin/agents")
                        .param("status", "PENDING_VERIFICATION").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.agentId == '%s')].fullName"
                        .formatted(agent.getId())).value("Kojo Agent"));

        // owner tokens are turned away by the security chain
        mockMvc.perform(get("/api/v1/admin/agents")
                        .header(HttpHeaders.AUTHORIZATION,
                                TestTokens.bearer(UUID.randomUUID(), "+233200000001")))
                .andExpect(status().isForbidden());

        // rejecting without a reason is a validation error
        mockMvc.perform(put("/api/v1/admin/agents/{id}/verify", agent.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":false,\"rejectionReason\":null}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/admin/agents/{id}/verify", agent.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":true,\"rejectionReason\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"));

        // a second decision conflicts
        mockMvc.perform(put("/api/v1/admin/agents/{id}/verify", agent.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":false,\"rejectionReason\":\"changed my mind\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.errorCode").value("ALREADY_DECIDED"));
    }

    @Test
    void agentHomeIsReachableForAnyVerificationStatus() throws Exception {
        InsuranceAgent rejected = newAgent(VerificationStatus.PENDING_VERIFICATION);
        String bearer = "Bearer " + TestTokens.token(rejected.getUserId(), "AGENT", "+233244111100");
        mockMvc.perform(get("/api/v1/agents/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.data.subscription").isEmpty());
    }
}
