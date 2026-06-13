package com.assetshield.marketplace.client;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** NOTIFICATIONS_MODE=log: records the would-be notification at INFO. */
public class LogNotificationClient implements NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(LogNotificationClient.class);

    @Override
    public void send(UUID userId, String type, String title, String body, Map<String, String> payload) {
        log.info("NOTIFICATION [{}] to user {}: '{}' — {} payload={}", type, userId, title, body, payload);
    }
}
