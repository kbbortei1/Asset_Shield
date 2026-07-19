package com.assetshield.marketplace.web;

import com.assetshield.marketplace.TestProps;
import com.assetshield.marketplace.client.AuthUserClient;
import com.assetshield.marketplace.client.DossierClient;
import com.assetshield.marketplace.client.PropertyClient;
import com.assetshield.marketplace.domain.AgentSubscription;
import com.assetshield.marketplace.domain.InsuranceAgent;
import com.assetshield.marketplace.domain.SubscriptionStatus;
import com.assetshield.marketplace.client.PaymentClient;
import com.assetshield.marketplace.domain.VerificationStatus;
import com.assetshield.marketplace.repo.AgentSubscriptionRepository;
import com.assetshield.marketplace.repo.InsuranceAgentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared Spring context for the Day 5 marketplace ITs: one PostgreSQL
 * container for the whole suite (singleton, deliberately NOT @Container so
 * the JUnit extension never stops it between classes) and the three
 * downstream clients mocked. Identical properties + mock set across
 * subclasses keep the context cacheable.
 */
@SpringBootTest(properties = {
        "JWT_SECRET=" + TestProps.JWT_SECRET,
        "INTERNAL_API_KEY=" + TestProps.INTERNAL_API_KEY
})
@AutoConfigureMockMvc
public abstract class MarketplaceITBase {

    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15");

    static {
        postgres.start();
    }

    private static final AtomicInteger LICENCE_SEQ = new AtomicInteger();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected InsuranceAgentRepository agentRepository;

    @Autowired
    protected AgentSubscriptionRepository agentSubscriptionRepository;

    @MockitoBean
    protected PropertyClient propertyClient;

    @MockitoBean
    protected DossierClient dossierClient;

    @MockitoBean
    protected AuthUserClient authUserClient;

    @MockitoBean
    protected PaymentClient paymentClient;

    // ── seed helpers ────────────────────────────────────────────────────────

    protected InsuranceAgent newAgent(VerificationStatus status) {
        InsuranceAgent agent = new InsuranceAgent();
        agent.setUserId(UUID.randomUUID());
        agent.setInsurerName("Star Assurance");
        agent.setNicLicenceNo("NIC-" + LICENCE_SEQ.incrementAndGet() + "-" + UUID.randomUUID());
        agent.setVerificationStatus(status);
        return agentRepository.save(agent);
    }

    protected InsuranceAgent subscribedAgent() {
        InsuranceAgent agent = newAgent(VerificationStatus.VERIFIED);
        activateSubscription(agent);
        return agent;
    }

    protected AgentSubscription activateSubscription(InsuranceAgent agent) {
        AgentSubscription sub = new AgentSubscription();
        sub.setAgentId(agent.getId());
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStartedAt(Instant.now());
        sub.setExpiresAt(Instant.now().plus(Duration.ofDays(30)));
        return agentSubscriptionRepository.save(sub);
    }

    protected static PropertyClient.PropertyInfo optedInProperty(UUID propertyId, UUID ownerId) {
        return new PropertyClient.PropertyInfo(propertyId, ownerId, "Adabraka Lodge",
                "RESIDENTIAL", "Adabraka", true, false);
    }

    protected static DossierClient.DossierMeta readyDossier(UUID dossierId, UUID propertyId, UUID ownerId) {
        return new DossierClient.DossierMeta(dossierId, ownerId, propertyId, UUID.randomUUID(),
                "READY", "abc123", "dossiers/d.pdf", new java.math.BigDecimal("12500.00"),
                "FLOOD", Instant.now().toString());
    }

    protected static Optional<AuthUserClient.AuthUserInfo> userInfo(UUID userId, String fullName) {
        return Optional.of(new AuthUserClient.AuthUserInfo(userId, fullName, "+233200000000",
                "OWNER", "ACTIVE"));
    }
}
