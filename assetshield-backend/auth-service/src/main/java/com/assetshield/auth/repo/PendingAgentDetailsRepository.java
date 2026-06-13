package com.assetshield.auth.repo;

import com.assetshield.auth.domain.PendingAgentDetails;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PendingAgentDetailsRepository extends JpaRepository<PendingAgentDetails, UUID> {

    boolean existsByNicLicenceNo(String nicLicenceNo);

    Optional<PendingAgentDetails> findByUserId(UUID userId);

    Optional<PendingAgentDetails> findByNicLicenceNo(String nicLicenceNo);

    /** Unsynced details whose owner finished OTP (ACTIVE) — the re-push set. */
    @Query("""
            select d from PendingAgentDetails d, User u
            where u.id = d.userId and d.consumedAt is null
              and u.status = com.assetshield.auth.domain.UserStatus.ACTIVE
              and u.deletedAt is null
            """)
    List<PendingAgentDetails> findUnconsumedForActiveUsers();
}
