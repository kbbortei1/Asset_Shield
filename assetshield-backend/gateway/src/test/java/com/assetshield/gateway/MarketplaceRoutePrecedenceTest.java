package com.assetshield.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The two Day 5 path-precedence traps: marketplace's /users/me/* and
 * /admin/agents and /dossiers/{id} slices must win over the broader auth
 * and damage routes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "JWT_SECRET=" + TestTokens.SECRET)
@AutoConfigureWebTestClient
class MarketplaceRoutePrecedenceTest {

    static StubDownstream auth = StubDownstream.start();
    static StubDownstream damage = StubDownstream.start();
    static StubDownstream marketplace = StubDownstream.start();

    @DynamicPropertySource
    static void routeToStubs(DynamicPropertyRegistry registry) {
        registry.add("AUTH_SERVICE_URI", auth::uri);
        registry.add("DAMAGE_SERVICE_URI", damage::uri);
        registry.add("MARKETPLACE_SERVICE_URI", marketplace::uri);
    }

    @AfterAll
    static void stopStubs() {
        auth.stop();
        damage.stop();
        marketplace.stop();
    }

    @Autowired
    WebTestClient client;

    @BeforeEach
    void resetStubs() {
        auth.reset();
        damage.reset();
        marketplace.reset();
    }

    private void getAs(String role, String path) {
        client.get().uri(path)
                .header("Authorization", "Bearer " + TestTokens.token(UUID.randomUUID(), role, 3600))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void usersMeMarketplaceSlicesGoToMarketplaceTheRestToAuth() {
        getAs("OWNER", "/api/v1/users/me/agent-interests");
        assertThat(marketplace.lastPath()).isEqualTo("/api/v1/users/me/agent-interests");

        getAs("OWNER", "/api/v1/users/me/quotes");
        assertThat(marketplace.lastPath()).isEqualTo("/api/v1/users/me/quotes");

        getAs("OWNER", "/api/v1/users/me/subscription");
        assertThat(marketplace.lastPath()).isEqualTo("/api/v1/users/me/subscription");

        marketplace.reset();
        getAs("OWNER", "/api/v1/users/me");
        assertThat(auth.lastPath()).isEqualTo("/api/v1/users/me");
        assertThat(marketplace.lastPath()).isNull();
    }

    @Test
    void adminAgentsGoesToMarketplaceAdminAdminsStaysOnAuth() {
        getAs("ADMIN", "/api/v1/admin/agents");
        assertThat(marketplace.lastPath()).isEqualTo("/api/v1/admin/agents");

        getAs("ADMIN", "/api/v1/admin/agents/" + UUID.randomUUID() + "/verify");
        assertThat(marketplace.lastPath()).contains("/verify");

        getAs("ADMIN", "/api/v1/admin/admins");
        assertThat(auth.lastPath()).isEqualTo("/api/v1/admin/admins");
    }

    @Test
    void dossierConsentSlicesGoToMarketplaceTheRestToDamage() {
        UUID dossierId = UUID.randomUUID();
        getAs("AGENT", "/api/v1/dossiers/" + dossierId + "/verify");
        assertThat(marketplace.lastPath()).endsWith("/verify");

        getAs("AGENT", "/api/v1/dossiers/" + dossierId + "/quote");
        assertThat(marketplace.lastPath()).endsWith("/quote");

        getAs("OWNER", "/api/v1/dossiers/" + dossierId + "/share-to-agent");
        assertThat(marketplace.lastPath()).endsWith("/share-to-agent");

        getAs("OWNER", "/api/v1/dossiers/" + dossierId + "/share-to-agent/" + UUID.randomUUID());
        assertThat(marketplace.lastPath()).contains("/share-to-agent/");

        damage.reset();
        getAs("OWNER", "/api/v1/dossiers/" + dossierId);
        assertThat(damage.lastPath()).isEqualTo("/api/v1/dossiers/" + dossierId);

        getAs("AGENT", "/api/v1/agents/me/leads");
        assertThat(marketplace.lastPath()).isEqualTo("/api/v1/agents/me/leads");
    }
}
