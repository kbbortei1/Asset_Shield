package com.assetshield.notification.service;

import com.assetshield.notification.domain.DeviceToken;
import com.assetshield.notification.domain.Platform;
import com.assetshield.notification.repo.DeviceTokenRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceTokenService {

    private static final Logger log = LoggerFactory.getLogger(DeviceTokenService.class);

    private final DeviceTokenRepository deviceTokenRepository;

    public DeviceTokenService(DeviceTokenRepository deviceTokenRepository) {
        this.deviceTokenRepository = deviceTokenRepository;
    }

    /**
     * Upsert. A token already registered to a DIFFERENT user (phone changed
     * hands, re-login on a shared device) is revoked there and re-created for
     * the caller — one live owner per physical device token (ux_device_token).
     */
    @Transactional
    public void register(UUID userId, String fcmToken, Platform platform) {
        DeviceToken existing = deviceTokenRepository.findByFcmTokenAndRevokedAtIsNull(fcmToken)
                .orElse(null);
        if (existing != null) {
            if (existing.getUserId().equals(userId)) {
                existing.setPlatform(platform);
                existing.setLastSeenAt(Instant.now());
                deviceTokenRepository.save(existing);
                return;
            }
            existing.setRevokedAt(Instant.now());
            deviceTokenRepository.saveAndFlush(existing); // flush: free the partial index slot
            log.info("Device token moved from user {} to user {}", existing.getUserId(), userId);
        }
        DeviceToken token = new DeviceToken();
        token.setUserId(userId);
        token.setFcmToken(fcmToken);
        token.setPlatform(platform);
        token.setLastSeenAt(Instant.now());
        deviceTokenRepository.save(token);
    }

    /** Logout hygiene — idempotent; unknown/foreign tokens are a silent no-op. */
    @Transactional
    public void revoke(UUID userId, String fcmToken) {
        deviceTokenRepository.findByFcmTokenAndRevokedAtIsNull(fcmToken)
                .filter(token -> token.getUserId().equals(userId))
                .ifPresent(token -> {
                    token.setRevokedAt(Instant.now());
                    deviceTokenRepository.save(token);
                });
    }
}
