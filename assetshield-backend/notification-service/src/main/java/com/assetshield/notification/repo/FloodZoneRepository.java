package com.assetshield.notification.repo;

import com.assetshield.notification.domain.FloodZone;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FloodZoneRepository extends JpaRepository<FloodZone, UUID> {
}
