package com.assetshield.damage.client;

import java.util.Map;
import java.util.UUID;

/**
 * Notification dispatch (DOSSIER_READY). NOTIFICATIONS_MODE selects log
 * (Days 1-5) or remote (Day 6+: notification-service).
 */
public interface NotificationClient {

    void send(UUID userId, String type, String title, String body, Map<String, String> payload);
}
