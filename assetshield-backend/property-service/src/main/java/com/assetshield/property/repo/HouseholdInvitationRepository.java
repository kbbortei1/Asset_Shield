package com.assetshield.property.repo;

import com.assetshield.property.domain.HouseholdInvitation;
import com.assetshield.property.domain.InvitationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HouseholdInvitationRepository extends JpaRepository<HouseholdInvitation, UUID> {

    Optional<HouseholdInvitation> findByPropertyIdAndInviteePhoneAndStatus(
            UUID propertyId, String inviteePhone, InvitationStatus status);

    /** PENDING, unexpired invitations addressed to me (by user id or phone claim). */
    @Query("""
            select i from HouseholdInvitation i
            where i.status = com.assetshield.property.domain.InvitationStatus.PENDING
              and i.expiresAt > :now
              and (i.inviteeUserId = :userId or i.inviteePhone = :phone)
            order by i.createdAt desc
            """)
    List<HouseholdInvitation> findPendingFor(@Param("userId") UUID userId,
                                             @Param("phone") String phone,
                                             @Param("now") Instant now);
}
