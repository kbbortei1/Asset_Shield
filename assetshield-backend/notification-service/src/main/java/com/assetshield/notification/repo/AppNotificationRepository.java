package com.assetshield.notification.repo;

import com.assetshield.notification.domain.AppNotification;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppNotificationRepository extends JpaRepository<AppNotification, UUID> {

    Page<AppNotification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
