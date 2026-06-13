package com.assetshield.marketplace.repo;

import com.assetshield.marketplace.domain.DossierShare;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DossierShareRepository extends JpaRepository<DossierShare, UUID> {

    Optional<DossierShare> findByDossierIdAndAgentIdAndRevokedAtIsNull(UUID dossierId, UUID agentId);

    boolean existsByDossierIdAndAgentIdAndRevokedAtIsNull(UUID dossierId, UUID agentId);

    /** Unrevoked shares whose backing interest is still ACCEPTED, newest first. */
    @Query("""
            select s from DossierShare s, AgentInterest i
            where i.id = s.agentInterestId
              and s.agentId = :agentId and s.revokedAt is null and i.status = 'ACCEPTED'
            order by s.consentAt desc
            """)
    Page<DossierShare> findActiveByAgent(@Param("agentId") UUID agentId, Pageable pageable);

    // flushAutomatically (not clearAutomatically): a bulk update with a cleared
    // context silently discards unflushed persists queued earlier in the tx.
    @Modifying(flushAutomatically = true)
    @Query("""
            update DossierShare s set s.revokedAt = :now
            where s.agentInterestId = :interestId and s.revokedAt is null
            """)
    int revokeByInterest(@Param("interestId") UUID interestId, @Param("now") Instant now);
}
