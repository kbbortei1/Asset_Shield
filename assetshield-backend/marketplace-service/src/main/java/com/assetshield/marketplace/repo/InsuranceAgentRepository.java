package com.assetshield.marketplace.repo;

import com.assetshield.marketplace.domain.InsuranceAgent;
import com.assetshield.marketplace.domain.VerificationStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InsuranceAgentRepository extends JpaRepository<InsuranceAgent, UUID> {

    Optional<InsuranceAgent> findByUserId(UUID userId);

    Optional<InsuranceAgent> findByNicLicenceNo(String nicLicenceNo);

    Page<InsuranceAgent> findByVerificationStatus(VerificationStatus status, Pageable pageable);
}
