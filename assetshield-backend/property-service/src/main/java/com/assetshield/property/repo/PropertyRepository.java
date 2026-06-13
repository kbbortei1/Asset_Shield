package com.assetshield.property.repo;

import com.assetshield.property.domain.Property;
import com.assetshield.property.domain.PropertyType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PropertyRepository extends JpaRepository<Property, UUID> {

    Optional<Property> findByIdAndDeletedAtIsNull(UUID id);

    long countByOwnerUserIdAndDeletedAtIsNull(UUID ownerUserId);

    /** Owned + member properties (union, no duplicates), newest first. */
    @Query("""
            select p from Property p
            where p.deletedAt is null
              and (p.ownerUserId = :userId
                   or p.id in (select m.propertyId from HouseholdMembership m
                               where m.memberUserId = :userId and m.revokedAt is null))
            order by p.createdAt desc
            """)
    Page<Property> findAccessible(@Param("userId") UUID userId, Pageable pageable);

    // flushAutomatically (not clearAutomatically): a bulk update with a cleared
    // context silently discards unflushed persists queued earlier in the tx.
    @Modifying(flushAutomatically = true)
    @Query("update Property p set p.deletedAt = :now where p.id = :id and p.deletedAt is null")
    int softDelete(@Param("id") UUID id, @Param("now") Instant now);

    // Marketplace leads: opted-in, live properties only (ix_properties_optin).
    // Two variants instead of nullable parameters: PostgreSQL types a null
    // bind inside lower(?) as bytea and the query blows up at runtime.
    // Callers pass locality = "" for "no locality filter".

    @Query("""
            select p from Property p
            where p.deletedAt is null and p.openToOffers = true
              and lower(p.locality) like lower(concat('%', :locality, '%'))
            order by p.openToOffersAt desc
            """)
    Page<Property> findLeads(@Param("locality") String locality, Pageable pageable);

    @Query("""
            select p from Property p
            where p.deletedAt is null and p.openToOffers = true
              and p.type = :type
              and lower(p.locality) like lower(concat('%', :locality, '%'))
            order by p.openToOffersAt desc
            """)
    Page<Property> findLeadsByType(@Param("type") PropertyType type,
                                   @Param("locality") String locality, Pageable pageable);

    /** Redoc sweep: documented at least once, but not within the window. */
    @Query("""
            select p from Property p
            where p.deletedAt is null
              and p.lastDocumentedAt is not null and p.lastDocumentedAt < :cutoff
            order by p.lastDocumentedAt asc
            """)
    Page<Property> findStaleDocumentation(@Param("cutoff") Instant cutoff, Pageable pageable);
}
