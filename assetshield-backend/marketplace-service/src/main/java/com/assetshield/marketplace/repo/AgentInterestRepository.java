package com.assetshield.marketplace.repo;

import com.assetshield.marketplace.domain.AgentInterest;
import com.assetshield.marketplace.domain.InterestStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentInterestRepository extends JpaRepository<AgentInterest, UUID> {

    boolean existsByAgentIdAndPropertyIdAndStatus(UUID agentId, UUID propertyId, InterestStatus status);

    Page<AgentInterest> findByAgentIdOrderByCreatedAtDesc(UUID agentId, Pageable pageable);

    Page<AgentInterest> findByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId, Pageable pageable);

    Page<AgentInterest> findByOwnerUserIdAndStatusOrderByCreatedAtDesc(
            UUID ownerUserId, InterestStatus status, Pageable pageable);

    // flushAutomatically (not clearAutomatically): a bulk update with a cleared
    // context silently discards unflushed persists queued earlier in the tx.
    @Modifying(flushAutomatically = true)
    @Query("""
            update AgentInterest i set i.status = 'DECLINED', i.respondedAt = :now
            where i.propertyId = :propertyId and i.status = 'PENDING'
            """)
    int declinePendingForProperty(@Param("propertyId") UUID propertyId, @Param("now") Instant now);
}
