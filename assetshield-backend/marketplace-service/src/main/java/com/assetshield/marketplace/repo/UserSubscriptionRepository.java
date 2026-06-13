package com.assetshield.marketplace.repo;

import com.assetshield.marketplace.domain.SubscriptionStatus;
import com.assetshield.marketplace.domain.UserSubscription;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, UUID> {

    Optional<UserSubscription> findByUserIdAndStatus(UUID userId, SubscriptionStatus status);

    List<UserSubscription> findByStatusAndExpiresAtLessThanEqual(SubscriptionStatus status, Instant cutoff);

    List<UserSubscription> findByStatusAndExpiresAtBetweenAndExpiryWarnedAtIsNull(
            SubscriptionStatus status, Instant from, Instant to);
}
