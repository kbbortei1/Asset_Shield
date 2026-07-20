package com.assetshield.notification.repo;

import com.assetshield.notification.domain.MaintenanceReminder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceReminderRepository
        extends JpaRepository<MaintenanceReminder, MaintenanceReminder.Key> {
}
