package com.assetshield.marketplace.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.assetshield.marketplace.TestTokens;
import com.assetshield.marketplace.client.DossierClient;
import com.assetshield.marketplace.domain.InsuranceAgent;
import com.assetshield.marketplace.domain.InterestStatus;
import com.assetshield.marketplace.repo.AgentInterestRepository;
import com.assetshield.marketplace.share.QuoteService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class ShareQuoteIT extends MarketplaceITBase {

    @Autowired
    AgentInterestRepository interestRepository;

    private static String agentBearer(InsuranceAgent agent) {
        return "Bearer " + TestTokens.token(agent.getUserId(), "AGENT", "+233244111111");
    }

    private static String ownerBearer(UUID ownerId) {
        return TestTokens.bearer(ownerId, "+233200000001");
    }

    private record Setup(InsuranceAgent agent, UUID propertyId, UUID ownerId, UUID interestId) {
    }

    /** Subscribed agent with an ACCEPTED interest on an opted-in property. */
    private Setup acceptedConnection() throws Exception {
        InsuranceAgent agent = subscribedAgent();
        UUID propertyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(propertyClient.property(eq(propertyId)))
                .thenReturn(Optional.of(optedInProperty(propertyId, ownerId)));
        MvcResult result = mockMvc.perform(post("/api/v1/leads/{id}/express-interest", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, agentBearer(agent)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID interestId = UUID.fromString(objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("data").get("interestId").asString());
        mockMvc.perform(put("/api/v1/agent-interests/{id}/respond", interestId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accept\":true}"))
                .andExpect(status().isOk());
        return new Setup(agent, propertyId, ownerId, interestId);
    }

    private void share(Setup setup, UUID dossierId, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/dossiers/{id}/share-to-agent", dossierId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer(setup.ownerId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentInterestId\":\"" + setup.interestId() + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    // ── share validation ─────────────────────────────────────────────────────

    @Test
    void shareValidationRejectsNotReadyMismatchedPropertyAndDuplicates() throws Exception {
        Setup setup = acceptedConnection();
        when(propertyClient.access(eq(setup.propertyId()), eq(setup.ownerId()))).thenReturn("OWNER");

        // dossier not READY → 400
        UUID pendingDossier = UUID.randomUUID();
        when(dossierClient.meta(eq(pendingDossier)))
                .thenReturn(Optional.of(new DossierClient.DossierMeta(pendingDossier,
                        setup.ownerId(), setup.propertyId(), UUID.randomUUID(),
                        "PENDING_PAYMENT", null, null, null, "FLOOD", null)));
        share(setup, pendingDossier, 400);

        // dossier belongs to a different property than the interest → 400
        UUID otherPropertyDossier = UUID.randomUUID();
        UUID otherPropertyId = UUID.randomUUID();
        when(dossierClient.meta(eq(otherPropertyDossier)))
                .thenReturn(Optional.of(readyDossier(otherPropertyDossier, otherPropertyId,
                        setup.ownerId())));
        when(propertyClient.access(eq(otherPropertyId), eq(setup.ownerId()))).thenReturn("OWNER");
        share(setup, otherPropertyDossier, 400);

        // happy path, then a duplicate live share → 409
        UUID dossierId = UUID.randomUUID();
        when(dossierClient.meta(eq(dossierId)))
                .thenReturn(Optional.of(readyDossier(dossierId, setup.propertyId(), setup.ownerId())));
        share(setup, dossierId, 201);
        mockMvc.perform(post("/api/v1/dossiers/{id}/share-to-agent", dossierId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer(setup.ownerId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentInterestId\":\"" + setup.interestId() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.errorCode").value("ALREADY_SHARED"));
    }

    @Test
    void nonOwnerCallersCannotManageShares() throws Exception {
        Setup setup = acceptedConnection();
        UUID dossierId = UUID.randomUUID();
        when(dossierClient.meta(eq(dossierId)))
                .thenReturn(Optional.of(readyDossier(dossierId, setup.propertyId(), setup.ownerId())));
        UUID stranger = UUID.randomUUID();
        when(propertyClient.access(eq(setup.propertyId()), eq(stranger))).thenReturn("NONE");
        mockMvc.perform(post("/api/v1/dossiers/{id}/share-to-agent", dossierId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentInterestId\":\"" + setup.interestId() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.errorCode").value("NOT_OWNER"));
    }

    @Test
    void revokingTheShareAloneCutsDossierAccessButKeepsTheConnection() throws Exception {
        Setup setup = acceptedConnection();
        UUID dossierId = UUID.randomUUID();
        when(propertyClient.access(eq(setup.propertyId()), eq(setup.ownerId()))).thenReturn("OWNER");
        when(dossierClient.meta(eq(dossierId)))
                .thenReturn(Optional.of(readyDossier(dossierId, setup.propertyId(), setup.ownerId())));
        share(setup, dossierId, 201);

        mockMvc.perform(delete("/api/v1/dossiers/{id}/share-to-agent/{agentId}",
                        dossierId, setup.agent().getId())
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer(setup.ownerId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/dossiers/{id}/verify", dossierId)
                        .header(HttpHeaders.AUTHORIZATION, agentBearer(setup.agent())))
                .andExpect(status().isNotFound());
        // the connection itself is untouched
        assertThat(interestRepository.findById(setup.interestId()).orElseThrow().getStatus())
                .isEqualTo(InterestStatus.ACCEPTED);
    }

    // ── verify + quote flow ──────────────────────────────────────────────────

    @Test
    void agentVerifiesIntegrityThenQuotesAndOwnerAcceptanceLogsTheReferral() throws Exception {
        Setup setup = acceptedConnection();
        UUID dossierId = UUID.randomUUID();
        when(propertyClient.access(eq(setup.propertyId()), eq(setup.ownerId()))).thenReturn("OWNER");
        when(dossierClient.meta(eq(dossierId)))
                .thenReturn(Optional.of(readyDossier(dossierId, setup.propertyId(), setup.ownerId())));
        share(setup, dossierId, 201);

        when(dossierClient.verify(eq(dossierId)))
                .thenReturn(Optional.of(new DossierClient.DossierVerify(
                        "abc123", "abc123", true, 14, List.of())));
        mockMvc.perform(get("/api/v1/dossiers/{id}/verify", dossierId)
                        .header(HttpHeaders.AUTHORIZATION, agentBearer(setup.agent())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tamperEvident").value(true))
                .andExpect(jsonPath("$.data.photoCount").value(14))
                .andExpect(jsonPath("$.data.verifiedAt").exists());

        MvcResult quoteResult = mockMvc.perform(post("/api/v1/dossiers/{id}/quote", dossierId)
                        .header(HttpHeaders.AUTHORIZATION, agentBearer(setup.agent()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverageAmount\":50000.00,\"premium\":450.00,\"termMonths\":12}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        UUID quoteId = UUID.fromString(objectMapper
                .readTree(quoteResult.getResponse().getContentAsString())
                .get("data").get("quoteId").asString());

        when(authUserClient.byId(eq(setup.agent().getUserId())))
                .thenReturn(userInfo(setup.agent().getUserId(), "Kojo Agent"));
        when(propertyClient.propertyCached(eq(setup.propertyId())))
                .thenReturn(Optional.of(optedInProperty(setup.propertyId(), setup.ownerId())));
        mockMvc.perform(get("/api/v1/users/me/quotes")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer(setup.ownerId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].quoteId").value(quoteId.toString()))
                .andExpect(jsonPath("$.data.items[0].coverageAmount").value(50000.00))
                .andExpect(jsonPath("$.data.items[0].insurerName").value("Star Assurance"));

        // capture the billable referral INFO line
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger quoteLogger = (Logger) LoggerFactory.getLogger(QuoteService.class);
        quoteLogger.addAppender(appender);
        try {
            mockMvc.perform(put("/api/v1/quotes/{id}/respond", quoteId)
                            .header(HttpHeaders.AUTHORIZATION, ownerBearer(setup.ownerId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"accept\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
        } finally {
            quoteLogger.detachAppender(appender);
        }
        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel().toString()).isEqualTo("INFO");
            assertThat(event.getFormattedMessage())
                    .contains("REFERRAL")
                    .contains(quoteId.toString())
                    .contains(setup.agent().getId().toString());
        });

        // double respond → 409
        mockMvc.perform(put("/api/v1/quotes/{id}/respond", quoteId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer(setup.ownerId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accept\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.errorCode").value("ALREADY_RESPONDED"));
    }
}
