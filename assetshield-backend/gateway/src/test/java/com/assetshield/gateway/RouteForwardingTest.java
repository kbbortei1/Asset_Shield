package com.assetshield.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "JWT_SECRET=" + TestTokens.SECRET)
@AutoConfigureWebTestClient
class RouteForwardingTest {

    static StubDownstream downstream = StubDownstream.start();

    @DynamicPropertySource
    static void routeToStub(DynamicPropertyRegistry registry) {
        registry.add("AUTH_SERVICE_URI", downstream::uri);
    }

    @AfterAll
    static void stopStub() {
        downstream.stop();
    }

    @Autowired
    WebTestClient client;

    @Test
    void publicAuthPathForwardsToAuthServiceAndCarriesRequestId() {
        client.post().uri("/api/v1/auth/login")
                .bodyValue("{\"phoneNumber\":\"+233200000001\",\"password\":\"secret123\"}")
                .header("Content-Type", "application/json")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Request-Id")
                .expectBody().jsonPath("$.ok").isEqualTo(true);

        assertThat(downstream.lastRequestHeaders().getFirst("X-Request-Id")).isNotBlank();
    }

    @Test
    void validTokenForwardsIdentityHeadersAndStripsClientSuppliedOnes() {
        UUID userId = UUID.randomUUID();
        client.get().uri("/api/v1/users/me")
                .header("Authorization", "Bearer " + TestTokens.token(userId, "OWNER", 3600))
                .header("X-User-Id", "spoofed-id")
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus().isOk();

        assertThat(downstream.lastRequestHeaders().getFirst("X-User-Id")).isEqualTo(userId.toString());
        assertThat(downstream.lastRequestHeaders().getFirst("X-User-Role")).isEqualTo("OWNER");
    }
}
