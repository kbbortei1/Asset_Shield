package com.assetshield.marketplace.repo;

import com.assetshield.marketplace.domain.AgentSubscription;
import com.assetshield.marketplace.domain.SubscriptionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentSubscriptionRepository extends JpaRepository<AgentSubscription, UUID> {

    Optional<AgentSubscription> findByAgentIdAndStatus(UUID agentId, SubscriptionStatus status);

    Optional<AgentSubscription> findFirstByAgentIdOrderByExpiresAtDesc(UUID agentId);

    List<AgentSubscription> findByStatusAndExpiresAtLessThanEqual(SubscriptionStatus status, Instant cutoff);

    List<AgentSubscription> findByStatusAndExpiresAtBetweenAndExpiryWarnedAtIsNull(
            SubscriptionStatus status, Instant from, Instant to);
}
