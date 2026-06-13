package com.assetshield.notification.repo;

import com.assetshield.notification.domain.Tip;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TipRepository extends JpaRepository<Tip, UUID> {

    Page<Tip> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Tip> findByUserIdAndPropertyIdOrderByCreatedAtDesc(UUID userId, UUID propertyId,
                                                            Pageable pageable);

    /** Template ids already instantiated for this user+property (non-repetition). */
    @Query("select t.tipTemplateId from Tip t where t.userId = :userId and t.propertyId = :propertyId")
    List<UUID> usedTemplateIds(@Param("userId") UUID userId, @Param("propertyId") UUID propertyId);

    List<Tip> findByUserIdAndDeliveredAtIsNull(UUID userId);

    boolean existsByUserIdAndPropertyIdAndDeliveredAtIsNull(UUID userId, UUID propertyId);

    /** Users with anything waiting in the feed (ix_tips_undelivered). */
    @Query("select distinct t.userId from Tip t where t.deliveredAt is null")
    List<UUID> userIdsWithUndelivered();

    /** Every user who has ever received a tip (delivery sweep candidates). */
    @Query("select distinct t.userId from Tip t")
    List<UUID> allUserIds();

    /** Every property this user has ever received tips for. */
    @Query("select distinct t.propertyId from Tip t where t.userId = :userId")
    List<UUID> propertyIdsForUser(@Param("userId") UUID userId);
}
