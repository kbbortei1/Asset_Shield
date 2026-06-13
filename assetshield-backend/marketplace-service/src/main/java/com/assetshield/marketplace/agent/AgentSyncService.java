package com.assetshield.marketplace.agent;

import com.assetshield.marketplace.common.ApiException;
import com.assetshield.marketplace.common.ErrorCode;
import com.assetshield.marketplace.domain.AgentInterest;
import com.assetshield.marketplace.domain.InsuranceAgent;
import com.assetshield.marketplace.repo.AgentInterestRepository;
import com.assetshield.marketplace.repo.InsuranceAgentRepository;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.AgentSyncRequest;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.AgentSyncResponse;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal handlers for the auth→marketplace and property→marketplace pushes. */
@Service
public class AgentSyncService {

    private static final Logger log = LoggerFactory.getLogger(AgentSyncService.class);

    private final InsuranceAgentRepository agentRepository;
    private final AgentInterestRepository interestRepository;

    public AgentSyncService(InsuranceAgentRepository agentRepository,
                            AgentInterestRepository interestRepository) {
        this.agentRepository = agentRepository;
        this.interestRepository = interestRepository;
    }

    /** Idempotent on user_id; licence collision with a different user → 409. */
    @Transactional
    public AgentSyncResponse sync(AgentSyncRequest request) {
        Optional<InsuranceAgent> existing = agentRepository.findByUserId(request.userId());
        if (existing.isPresent()) {
            InsuranceAgent agent = existing.get();
            return new AgentSyncResponse(agent.getId(), agent.getVerificationStatus().name());
        }
        String licence = request.nicLicenceNo().trim();
        agentRepository.findByNicLicenceNo(licence)
                .filter(other -> !other.getUserId().equals(request.userId()))
                .ifPresent(other -> {
                    throw new ApiException(ErrorCode.LICENCE_EXISTS,
                            "NIC licence number is already registered");
                });
        InsuranceAgent agent = new InsuranceAgent();
        agent.setUserId(request.userId());
        agent.setInsurerName(request.insurerName().trim());
        agent.setNicLicenceNo(licence);
        agent = agentRepository.save(agent);
        log.info("Agent synced from auth: user={} agent={}", request.userId(), agent.getId());
        return new AgentSyncResponse(agent.getId(), agent.getVerificationStatus().name());
    }

    /**
     * Opt-out auto-declines PENDING interests on the property. ACCEPTED
     * connections survive — the owner revokes those individually.
     */
    @Transactional
    public void optInChanged(UUID propertyId, boolean openToOffers) {
        if (openToOffers) {
            return;
        }
        int declined = interestRepository.declinePendingForProperty(propertyId, Instant.now());
        if (declined > 0) {
            log.info("Opt-out for property {} auto-declined {} pending interests",
                    propertyId, declined);
        }
    }
}
