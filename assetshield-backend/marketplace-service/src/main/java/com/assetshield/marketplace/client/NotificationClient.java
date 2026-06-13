package com.assetshield.marketplace.client;

import java.util.Map;
import java.util.UUID;

/**
 * Push/SMS notification dispatch. notification-service arrives Day 6;
 * NOTIFICATIONS_MODE=log is the only implementation today.
 */
public interface NotificationClient {

    void send(UUID userId, String type, String title, String body, Map<String, String> payload);
}
