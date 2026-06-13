package com.assetshield.marketplace.agent;

import com.assetshield.marketplace.common.ApiException;
import com.assetshield.marketplace.common.ErrorCode;
import com.assetshield.marketplace.domain.InsuranceAgent;
import com.assetshield.marketplace.domain.SubscriptionStatus;
import com.assetshield.marketplace.domain.VerificationStatus;
import com.assetshield.marketplace.repo.AgentSubscriptionRepository;
import com.assetshield.marketplace.repo.InsuranceAgentRepository;
import com.assetshield.marketplace.security.AuthUser;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * The two composable marketplace guards. Gate A: the caller is a VERIFIED
 * agent. Gate B: Gate A plus an ACTIVE, unexpired subscription. Lapse never
 * deletes data — these gates only deny access.
 */
@Component
public class AgentGates {

    private final InsuranceAgentRepository agentRepository;
    private final AgentSubscriptionRepository subscriptionRepository;

    public AgentGates(InsuranceAgentRepository agentRepository,
                      AgentSubscriptionRepository subscriptionRepository) {
        this.agentRepository = agentRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    /** Any-status agent record (the agent-home screen needs it pre-verification). */
    public InsuranceAgent requireAgentRecord(AuthUser user) {
        if (!"AGENT".equals(user.role())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Agent account required");
        }
        return agentRepository.findByUserId(user.id())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Agent profile not found"));
    }

    /** Gate A. */
    public InsuranceAgent requireVerifiedAgent(AuthUser user) {
        if (!"AGENT".equals(user.role())) {
            throw new ApiException(ErrorCode.AGENT_NOT_VERIFIED, "Verified agent account required");
        }
        InsuranceAgent agent = agentRepository.findByUserId(user.id()).orElse(null);
        if (agent == null || agent.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new ApiException(ErrorCode.AGENT_NOT_VERIFIED, "Verified agent account required");
        }
        return agent;
    }

    /** Gate B. */
    public InsuranceAgent requireSubscribedAgent(AuthUser user) {
        InsuranceAgent agent = requireVerifiedAgent(user);
        boolean active = subscriptionRepository
                .findByAgentIdAndStatus(agent.getId(), SubscriptionStatus.ACTIVE)
                .filter(sub -> sub.getExpiresAt().isAfter(Instant.now()))
                .isPresent();
        if (!active) {
            throw new ApiException(ErrorCode.SUBSCRIPTION_INACTIVE,
                    "An active subscription is required");
        }
        return agent;
    }
}
