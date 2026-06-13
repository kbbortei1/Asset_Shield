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
 * Day 6 path-precedence: the notification slices of /users/me/* and
 * /properties/{id} must win over the broader auth/property routes — without
 * stealing marketplace's /users/me/* slices or damage's
 * /properties/{id}/damage-reports.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "JWT_SECRET=" + TestTokens.SECRET)
@AutoConfigureWebTestClient
class NotificationRoutePrecedenceTest {

    static StubDownstream auth = StubDownstream.start();
    static StubDownstream property = StubDownstream.start();
    static StubDownstream damage = StubDownstream.start();
    static StubDownstream marketplace = StubDownstream.start();
    static StubDownstream notification = StubDownstream.start();

    @DynamicPropertySource
    static void routeToStubs(DynamicPropertyRegistry registry) {
        registry.add("AUTH_SERVICE_URI", auth::uri);
        registry.add("PROPERTY_SERVICE_URI", property::uri);
        registry.add("DAMAGE_SERVICE_URI", damage::uri);
        registry.add("MARKETPLACE_SERVICE_URI", marketplace::uri);
        registry.add("NOTIFICATION_SERVICE_URI", notification::uri);
    }

    @AfterAll
    static void stopStubs() {
        auth.stop();
        property.stop();
        damage.stop();
        marketplace.stop();
        notification.stop();
    }

    @Autowired
    WebTestClient client;

    @BeforeEach
    void resetStubs() {
        auth.reset();
        property.reset();
        damage.reset();
        marketplace.reset();
        notification.reset();
    }

    private void getAs(String role, String path) {
        client.get().uri(path)
                .header("Authorization", "Bearer " + TestTokens.token(UUID.randomUUID(), role, 3600))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void notificationUsersMeSlicesGoToNotificationService() {
        getAs("OWNER", "/api/v1/users/me/device-token");
        assertThat(notification.lastPath()).isEqualTo("/api/v1/users/me/device-token");

        getAs("OWNER", "/api/v1/users/me/notification-preferences");
        assertThat(notification.lastPath()).isEqualTo("/api/v1/users/me/notification-preferences");

        getAs("OWNER", "/api/v1/users/me/notifications");
        assertThat(notification.lastPath()).isEqualTo("/api/v1/users/me/notifications");
    }

    @Test
    void tipsPathsGoToNotificationService() {
        getAs("OWNER", "/api/v1/tips/feed");
        assertThat(notification.lastPath()).isEqualTo("/api/v1/tips/feed");

        UUID propertyId = UUID.randomUUID();
        getAs("OWNER", "/api/v1/properties/" + propertyId + "/tips");
        assertThat(notification.lastPath()).endsWith("/tips");
        assertThat(property.lastPath()).isNull();
    }

    @Test
    void originalRoutesAreNotShadowed() {
        getAs("OWNER", "/api/v1/users/me");
        assertThat(auth.lastPath()).isEqualTo("/api/v1/users/me");
        assertThat(notification.lastPath()).isNull();

        getAs("OWNER", "/api/v1/users/me/agent-interests");
        assertThat(marketplace.lastPath()).isEqualTo("/api/v1/users/me/agent-interests");

        UUID propertyId = UUID.randomUUID();
        getAs("OWNER", "/api/v1/properties/" + propertyId);
        assertThat(property.lastPath()).isEqualTo("/api/v1/properties/" + propertyId);

        getAs("OWNER", "/api/v1/properties/" + propertyId + "/damage-reports");
        assertThat(damage.lastPath()).endsWith("/damage-reports");
        assertThat(notification.lastPath()).isNull();
    }
}
