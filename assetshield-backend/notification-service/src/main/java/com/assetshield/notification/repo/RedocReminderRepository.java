package com.assetshield.notification.repo;

import com.assetshield.notification.domain.RedocReminder;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RedocReminderRepository extends JpaRepository<RedocReminder, UUID> {
}
