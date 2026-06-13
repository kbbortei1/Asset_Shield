package com.assetshield.marketplace.repo;

import com.assetshield.marketplace.domain.PolicyQuote;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolicyQuoteRepository extends JpaRepository<PolicyQuote, UUID> {

    @Query("""
            select q from PolicyQuote q, AgentInterest i
            where i.id = q.agentInterestId and i.ownerUserId = :ownerUserId
            order by q.createdAt desc
            """)
    Page<PolicyQuote> findByInterestOwner(@Param("ownerUserId") UUID ownerUserId, Pageable pageable);
}
