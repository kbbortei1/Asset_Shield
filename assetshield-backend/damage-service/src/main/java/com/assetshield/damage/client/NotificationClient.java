package com.assetshield.damage.client;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Notification dispatch — log-only until notification-service ships (Day 6).
 * Used for DOSSIER_READY today.
 */
@Component
public class NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

    public void send(UUID userId, String type, String title, String body, Map<String, String> payload) {
        log.info("NOTIFICATION [{}] to user {}: '{}' — {} payload={}", type, userId, title, body, payload);
    }
}
