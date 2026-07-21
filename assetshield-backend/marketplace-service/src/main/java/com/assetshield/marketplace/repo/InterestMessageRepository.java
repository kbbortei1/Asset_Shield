package com.assetshield.marketplace.repo;

import com.assetshield.marketplace.domain.InterestMessage;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterestMessageRepository extends JpaRepository<InterestMessage, UUID> {

    Page<InterestMessage> findByAgentInterestIdOrderByCreatedAtAsc(UUID agentInterestId, Pageable pageable);
}
