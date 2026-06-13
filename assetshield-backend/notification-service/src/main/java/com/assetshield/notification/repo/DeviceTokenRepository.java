package com.assetshield.notification.repo;

import com.assetshield.notification.domain.DeviceToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    Optional<DeviceToken> findByFcmTokenAndRevokedAtIsNull(String fcmToken);

    List<DeviceToken> findByUserIdAndRevokedAtIsNull(UUID userId);

    List<DeviceToken> findByFcmTokenInAndRevokedAtIsNull(List<String> fcmTokens);
}
