package com.assetshield.auth.service;

import com.assetshield.auth.client.MarketplaceAgentSyncClient;
import com.assetshield.auth.domain.PendingAgentDetails;
import com.assetshield.auth.repo.PendingAgentDetailsRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * auth → marketplace agent record sync. The OTP-completion push is
 * best-effort; the 60 s job re-pushes whatever marketplace has not consumed
 * yet, so agent visibility survives marketplace downtime — and agents who
 * registered before marketplace existed get synced on first boot.
 */
@Service
public class AgentSyncService {

    private static final Logger log = LoggerFactory.getLogger(AgentSyncService.class);

    private final PendingAgentDetailsRepository pendingAgentRepository;
    private final MarketplaceAgentSyncClient syncClient;

    public AgentSyncService(PendingAgentDetailsRepository pendingAgentRepository,
                            MarketplaceAgentSyncClient syncClient) {
        this.pendingAgentRepository = pendingAgentRepository;
        this.syncClient = syncClient;
    }

    /** Best-effort push right after the agent's OTP verification. Never throws. */
    @Transactional
    public void pushAfterVerification(UUID userId) {
        pendingAgentRepository.findByUserId(userId)
                .filter(details -> details.getConsumedAt() == null)
                .ifPresent(this::push);
    }

    /** Re-pushes unconsumed rows for verified (ACTIVE) users every 60 s. */
    @Scheduled(initialDelayString = "${app.agent-sync.initial-delay-ms:10000}",
            fixedDelayString = "${app.agent-sync.repush-delay-ms:60000}")
    @Transactional
    public void repushUnconsumed() {
        for (PendingAgentDetails details : pendingAgentRepository.findUnconsumedForActiveUsers()) {
            push(details);
        }
    }

    private void push(PendingAgentDetails details) {
        try {
            MarketplaceAgentSyncClient.SyncResult result = syncClient.sync(
                    details.getUserId(), details.getInsurerName(), details.getNicLicenceNo());
            details.setConsumedAt(Instant.now());
            pendingAgentRepository.save(details);
            if (result == MarketplaceAgentSyncClient.SyncResult.LICENCE_CONFLICT) {
                // consumed so we stop retrying — the agent simply never appears
                // verifiable until an admin untangles the licence collision
                log.error("Agent sync licence collision for user {} (licence {}): "
                                + "marketplace already has this licence under another user",
                        details.getUserId(), details.getNicLicenceNo());
            } else {
                log.info("Agent details synced to marketplace for user {}", details.getUserId());
            }
        } catch (Exception e) {
            log.warn("Agent sync to marketplace failed for user {} — will retry: {}",
                    details.getUserId(), e.getMessage());
        }
    }
}
