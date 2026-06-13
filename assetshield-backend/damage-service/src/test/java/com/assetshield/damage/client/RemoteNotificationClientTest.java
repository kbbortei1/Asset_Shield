package com.assetshield.damage.client;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Day 6 resilience flip: the remote client must swallow transport failures —
 * a dead notification-service never breaks the dossier READY transition
 * (DossierGeneratorService calls send() inline).
 */
class RemoteNotificationClientTest {

    @Test
    void deadNotificationServiceNeverThrowsIntoTheBusinessFlow() {
        // nothing listens on port 1 — every call fails at transport
        RemoteNotificationClient client =
                new RemoteNotificationClient("http://localhost:1", "test-key");

        assertThatCode(() -> client.send(UUID.randomUUID(), "DOSSIER_READY",
                "Your dossier is ready", "Download it from the app.",
                Map.of("dossierId", UUID.randomUUID().toString())))
                .doesNotThrowAnyException();
    }
}
