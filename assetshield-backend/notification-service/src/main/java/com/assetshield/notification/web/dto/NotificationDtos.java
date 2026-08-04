package com.assetshield.notification.web.dto;

import com.assetshield.notification.domain.NotificationType;
import com.assetshield.notification.domain.Platform;
import com.assetshield.notification.domain.TipsFrequency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    // ── device tokens + preferences ──────────────────────────────────────────

    public record DeviceTokenRequest(
            @NotBlank @Size(max = 512) String fcmToken,
            @NotNull Platform platform) {
    }

    public record DeviceTokenDeleteRequest(@NotBlank @Size(max = 512) String fcmToken) {
    }

    /** All fields optional — a partial update changes only what's provided. */
    public record PreferenceRequest(TipsFrequency tipsFrequency, Boolean pushEnabled, Boolean inAppEnabled) {
    }

    public record PreferenceResponse(TipsFrequency tipsFrequency, boolean pushEnabled, boolean inAppEnabled) {
    }

    // ── tips ─────────────────────────────────────────────────────────────────

    public record TipItem(UUID id, UUID propertyId, String tipText, String category,
                          Instant createdAt, Instant readAt) {
    }

    public record TipReadResponse(Instant readAt) {
    }

    // ── notification history ─────────────────────────────────────────────────

    public record NotificationItem(UUID id, NotificationType type, String title, String body,
                                   JsonNode payload, String status, Instant sentAt,
                                   Instant createdAt) {
    }

    // ── internal API ─────────────────────────────────────────────────────────

    public record InternalSendRequest(
            @NotNull UUID userId,
            @NotNull NotificationType type,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 500) String body,
            Map<String, Object> payload) {
    }

    /** Fan-out: the same notification to many users (admin broadcasts). */
    public record BulkSendRequest(
            @NotEmpty List<UUID> userIds,
            @NotNull NotificationType type,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 500) String body,
            Map<String, Object> payload,
            /** Admin-chosen channels; null → true. Still intersected with each user's prefs. */
            Boolean inApp,
            Boolean push) {
    }

    public record AssetCapturedRequest(@NotNull UUID userId, @NotNull UUID propertyId) {
    }

    public record AcceptedResponse(boolean accepted) {
    }
}
