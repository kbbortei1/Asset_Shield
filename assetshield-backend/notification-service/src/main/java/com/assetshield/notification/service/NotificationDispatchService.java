package com.assetshield.notification.service;

import com.assetshield.notification.domain.AppNotification;
import com.assetshield.notification.domain.DeviceToken;
import com.assetshield.notification.domain.NotificationStatus;
import com.assetshield.notification.domain.NotificationType;
import com.assetshield.notification.push.PushSender;
import com.assetshield.notification.repo.AppNotificationRepository;
import com.assetshield.notification.repo.DeviceTokenRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * The single dispatch pipeline: history row first (PENDING), then push to
 * every active device. No devices is still a successful delivery — the
 * in-app history is the contract; push is best-effort on top.
 */
@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final AppNotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final PushSender pushSender;
    private final ObjectMapper objectMapper;

    public NotificationDispatchService(AppNotificationRepository notificationRepository,
                                       DeviceTokenRepository deviceTokenRepository,
                                       PushSender pushSender, ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.pushSender = pushSender;
        this.objectMapper = objectMapper;
    }

    /** Fire-and-forget entry point — callers never block on FCM. */
    @Async("dispatchExecutor")
    @Transactional
    public void dispatchAsync(UUID userId, NotificationType type, String title, String body,
                              Map<String, Object> payload) {
        try {
            doDispatch(userId, type, title, body, payload);
        } catch (Exception e) {
            // an async failure has no caller to bubble to — log loudly
            log.error("Notification dispatch failed for user {} ({}): {}", userId, type, e.getMessage());
        }
    }

    /** Synchronous variant (schedulers and tests). */
    @Transactional
    public AppNotification dispatch(UUID userId, NotificationType type, String title, String body,
                                    Map<String, Object> payload) {
        return doDispatch(userId, type, title, body, payload);
    }

    private AppNotification doDispatch(UUID userId, NotificationType type, String title, String body,
                                       Map<String, Object> payload) {
        AppNotification notification = new AppNotification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setPayload(payload == null || payload.isEmpty()
                ? null : objectMapper.writeValueAsString(payload));
        notification = notificationRepository.saveAndFlush(notification);

        List<DeviceToken> devices = deviceTokenRepository.findByUserIdAndRevokedAtIsNull(userId);
        if (devices.isEmpty()) {
            // history-only delivery: nothing to push, nothing failed
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
            return notificationRepository.save(notification);
        }

        PushSender.PushOutcome outcome = pushSender.send(
                devices.stream().map(DeviceToken::getFcmToken).toList(),
                title, body, dataMap(type, payload));

        // token hygiene: FCM said these are dead — stop sending to them
        if (!outcome.invalidTokens().isEmpty()) {
            for (DeviceToken dead : deviceTokenRepository
                    .findByFcmTokenInAndRevokedAtIsNull(outcome.invalidTokens())) {
                dead.setRevokedAt(Instant.now());
                deviceTokenRepository.save(dead);
            }
        }

        if (outcome.successCount() > 0 || outcome.failureCount() == 0) {
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
        } else {
            notification.setStatus(NotificationStatus.FAILED);
        }
        return notificationRepository.save(notification);
    }

    /** FCM data values must be strings; the type rides along for deep links. */
    private static Map<String, String> dataMap(NotificationType type, Map<String, Object> payload) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", type.name());
        if (payload != null) {
            payload.forEach((key, value) -> data.put(key, String.valueOf(value)));
        }
        return data;
    }
}
