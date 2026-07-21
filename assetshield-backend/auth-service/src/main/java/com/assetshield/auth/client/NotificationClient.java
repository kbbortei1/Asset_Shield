package com.assetshield.auth.client;

import com.assetshield.auth.config.AppProperties;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Dispatches an in-app notification to a user via notification-service's
 * internal send endpoint (X-Internal-Api-Key). Used by the admin
 * "send announcement" tool. Throws on transport/5xx so the caller can surface
 * a failure to the admin.
 */
@Component
public class NotificationClient {

    private final RestClient restClient;

    public NotificationClient(AppProperties properties) {
        this.restClient = RestClient.builder()
                .requestFactory(InternalHttp.requestFactory())
                .baseUrl(properties.notificationServiceUri())
                .defaultHeader("X-Internal-Api-Key", properties.internalApiKey())
                .build();
    }

    /** Fan-out an announcement to many users in one call. */
    public void broadcast(List<UUID> userIds, String title, String body) {
        restClient.post()
                .uri("/internal/notifications/send-bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "userIds", userIds.stream().map(UUID::toString).toList(),
                        "type", "ANNOUNCEMENT",
                        "title", title,
                        "body", body))
                .retrieve()
                .toBodilessEntity();
    }
}
