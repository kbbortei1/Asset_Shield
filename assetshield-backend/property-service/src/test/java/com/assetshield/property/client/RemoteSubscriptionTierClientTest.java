package com.assetshield.property.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * TIER_LOOKUP_MODE=remote against a stub marketplace: parses the envelope,
 * caches per user, and fails CLOSED to FREE when marketplace is down.
 */
class RemoteSubscriptionTierClientTest {

    static HttpServer server;
    static AtomicReference<String> tier = new AtomicReference<>("PRO");
    static AtomicReference<String> lastApiKey = new AtomicReference<>();
    static AtomicInteger hits = new AtomicInteger();

    @BeforeAll
    static void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/users", exchange -> {
            hits.incrementAndGet();
            lastApiKey.set(exchange.getRequestHeaders().getFirst("X-Internal-Api-Key"));
            byte[] body = ("{\"status\":\"success\",\"data\":{\"tier\":\"" + tier.get()
                    + "\"},\"message\":\"Tier resolved\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    @Test
    void parsesTheTierSendsTheApiKeyAndCachesPerUser() {
        RemoteSubscriptionTierClient client = new RemoteSubscriptionTierClient(
                "http://localhost:" + server.getAddress().getPort(), "internal-key");
        UUID userId = UUID.randomUUID();

        tier.set("PRO");
        assertThat(client.tierFor(userId)).isEqualTo("PRO");
        assertThat(lastApiKey.get()).isEqualTo("internal-key");

        // second lookup is served from the 5-minute cache
        int before = hits.get();
        assertThat(client.tierFor(userId)).isEqualTo("PRO");
        assertThat(hits.get()).isEqualTo(before);

        tier.set("FREE");
        assertThat(client.tierFor(UUID.randomUUID())).isEqualTo("FREE");
    }

    @Test
    void unreachableMarketplaceFailsClosedToFree() {
        RemoteSubscriptionTierClient client = new RemoteSubscriptionTierClient(
                "http://localhost:1", "internal-key");
        assertThat(client.tierFor(UUID.randomUUID())).isEqualTo(SubscriptionTierClient.FREE);
    }
}
