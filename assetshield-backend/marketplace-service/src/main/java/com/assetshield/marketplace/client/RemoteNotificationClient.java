package com.assetshield.marketplace.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * NOTIFICATIONS_MODE=remote (Day 6+): POST notification:/internal/notifications/send.
 * Fire-and-forget — a dead notification-service must NEVER fail the business
 * operation; failures log WARN and the flow continues.
 */
public class RemoteNotificationClient implements NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(RemoteNotificationClient.class);

    private final RestClient restClient;

    public RemoteNotificationClient(String notificationUri, String internalApiKey) {
        this.restClient = RestClient.builder()
                .requestFactory(InternalHttp.requestFactory())
                .baseUrl(notificationUri)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .build();
    }

    @Override
    public void send(UUID userId, String type, String title, String body, Map<String, String> payload) {
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("userId", userId.toString());
            request.put("type", type);
            request.put("title", title);
            request.put("body", body);
            request.put("payload", payload == null ? Map.of() : payload);
            restClient.post()
                    .uri("/internal/notifications/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Notification [{}] to user {} failed: {}", type, userId, e.getMessage());
        }
    }
}
