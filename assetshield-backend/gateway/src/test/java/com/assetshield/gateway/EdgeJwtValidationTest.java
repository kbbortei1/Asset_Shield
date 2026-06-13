package com.assetshield.gateway;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "JWT_SECRET=" + TestTokens.SECRET)
@AutoConfigureWebTestClient
class EdgeJwtValidationTest {

    @Autowired
    WebTestClient client;

    @Test
    void missingTokenOnProtectedPathIsRejectedAtTheEdge() {
        client.get().uri("/api/v1/users/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.status").isEqualTo("error")
                .jsonPath("$.data.errorCode").isEqualTo("TOKEN_INVALID")
                .jsonPath("$.message").isNotEmpty();
    }

    @Test
    void malformedTokenIsRejected() {
        client.get().uri("/api/v1/users/me")
                .header("Authorization", "Bearer not.a.jwt")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.data.errorCode").isEqualTo("TOKEN_INVALID");
    }

    @Test
    void expiredTokenIsRejectedWithExpiredCode() {
        client.get().uri("/api/v1/users/me")
                .header("Authorization", "Bearer " + TestTokens.token(UUID.randomUUID(), "OWNER", -60))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.data.errorCode").isEqualTo("TOKEN_EXPIRED");
    }
}
