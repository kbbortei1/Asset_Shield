package com.assetshield.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "JWT_SECRET=" + TestTokens.SECRET)
@AutoConfigureWebTestClient
class RateLimitTest {

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
    void hammeringAPublicAuthPathReturns429WithEnvelope() {
        HttpStatusCode last = null;
        int tooMany = 0;
        for (int i = 0; i < 35; i++) {
            last = client.post().uri("/api/v1/auth/login")
                    .header("Content-Type", "application/json")
                    .bodyValue("{}")
                    .exchange()
                    .returnResult(String.class)
                    .getStatus();
            if (last.value() == 429) {
                tooMany++;
            }
        }
        assertThat(tooMany).isGreaterThan(0);
        assertThat(last.value()).isEqualTo(429);

        client.post().uri("/api/v1/auth/login")
                .header("Content-Type", "application/json")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectBody()
                .jsonPath("$.status").isEqualTo("error")
                .jsonPath("$.data.errorCode").isEqualTo("RATE_LIMITED");
    }
}
