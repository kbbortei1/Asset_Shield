package com.assetshield.notification.web;

import com.assetshield.notification.common.ApiResponse;
import com.assetshield.notification.common.PageEnvelope;
import com.assetshield.notification.repo.AppNotificationRepository;
import com.assetshield.notification.security.AuthUser;
import com.assetshield.notification.service.DeviceTokenService;
import com.assetshield.notification.service.PreferenceService;
import com.assetshield.notification.web.dto.NotificationDtos.DeviceTokenDeleteRequest;
import com.assetshield.notification.web.dto.NotificationDtos.DeviceTokenRequest;
import com.assetshield.notification.web.dto.NotificationDtos.NotificationItem;
import com.assetshield.notification.web.dto.NotificationDtos.PreferenceRequest;
import com.assetshield.notification.web.dto.NotificationDtos.PreferenceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Notification settings", description = "Device tokens, tip preferences, history")
public class UserSettingsController {

    private final DeviceTokenService deviceTokenService;
    private final PreferenceService preferenceService;
    private final AppNotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    public UserSettingsController(DeviceTokenService deviceTokenService,
                                  PreferenceService preferenceService,
                                  AppNotificationRepository notificationRepository,
                                  ObjectMapper objectMapper) {
        this.deviceTokenService = deviceTokenService;
        this.preferenceService = preferenceService;
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
    }

    private static AuthUser user(Authentication authentication) {
        return (AuthUser) authentication.getPrincipal();
    }

    @Operation(summary = "Register or refresh this device's FCM token")
    @PutMapping("/device-token")
    public ApiResponse<Map<String, Boolean>> registerToken(Authentication authentication,
                                                           @Valid @RequestBody DeviceTokenRequest request) {
        deviceTokenService.register(user(authentication).id(), request.fcmToken(), request.platform());
        return ApiResponse.success(Map.of("registered", true), "Device token registered");
    }

    @Operation(summary = "Revoke this device's FCM token (logout hygiene)")
    @DeleteMapping("/device-token")
    public ApiResponse<Map<String, Boolean>> revokeToken(Authentication authentication,
                                                         @Valid @RequestBody DeviceTokenDeleteRequest request) {
        deviceTokenService.revoke(user(authentication).id(), request.fcmToken());
        return ApiResponse.success(Map.of("revoked", true), "Device token revoked");
    }

    @Operation(summary = "Update notification preferences (tips frequency, push, in-app — all optional)")
    @PutMapping("/notification-preferences")
    public ApiResponse<PreferenceResponse> updatePreferences(Authentication authentication,
                                                             @Valid @RequestBody PreferenceRequest request) {
        PreferenceService.Prefs prefs = preferenceService.update(user(authentication).id(),
                request.tipsFrequency(), request.pushEnabled(), request.inAppEnabled());
        return ApiResponse.success(
                new PreferenceResponse(prefs.tipsFrequency(), prefs.pushEnabled(), prefs.inAppEnabled()),
                "Preferences updated");
    }

    @Operation(summary = "Current preferences (defaults: WEEKLY tips, push + in-app on)")
    @GetMapping("/notification-preferences")
    public ApiResponse<PreferenceResponse> preferences(Authentication authentication) {
        PreferenceService.Prefs prefs = preferenceService.get(user(authentication).id());
        return ApiResponse.success(
                new PreferenceResponse(prefs.tipsFrequency(), prefs.pushEnabled(), prefs.inAppEnabled()),
                "Preferences fetched");
    }

    @Operation(summary = "Notification history, newest first")
    @GetMapping("/notifications")
    public ApiResponse<PageEnvelope<NotificationItem>> notifications(Authentication authentication,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageEnvelope.of(notificationRepository
                .findByUserIdOrderByCreatedAtDesc(user(authentication).id(),
                        PageRequest.of(PageEnvelope.clampPage(page), PageEnvelope.clampSize(size)))
                .map(notification -> new NotificationItem(notification.getId(), notification.getType(),
                        notification.getTitle(), notification.getBody(),
                        notification.getPayload() == null
                                ? null : objectMapper.readTree(notification.getPayload()),
                        notification.getStatus().name(), notification.getSentAt(),
                        notification.getCreatedAt()))), "Notifications fetched");
    }
}
