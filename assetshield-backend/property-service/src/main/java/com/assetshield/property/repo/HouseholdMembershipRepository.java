package com.assetshield.property.repo;

import com.assetshield.property.domain.HouseholdMembership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdMembershipRepository extends JpaRepository<HouseholdMembership, UUID> {

    Optional<HouseholdMembership> findByPropertyIdAndMemberUserIdAndRevokedAtIsNull(
            UUID propertyId, UUID memberUserId);

    List<HouseholdMembership> findByPropertyIdAndRevokedAtIsNullOrderByCreatedAtAsc(UUID propertyId);

    boolean existsByPropertyIdAndMemberUserIdAndRevokedAtIsNull(UUID propertyId, UUID memberUserId);
}
