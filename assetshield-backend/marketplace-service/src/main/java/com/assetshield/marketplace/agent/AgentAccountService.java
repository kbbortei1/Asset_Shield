package com.assetshield.marketplace.agent;

import com.assetshield.marketplace.config.AppProperties;
import com.assetshield.marketplace.domain.AgentSubscription;
import com.assetshield.marketplace.domain.InsuranceAgent;
import com.assetshield.marketplace.domain.PaymentPurpose;
import com.assetshield.marketplace.payment.PaymentService;
import com.assetshield.marketplace.repo.AgentSubscriptionRepository;
import com.assetshield.marketplace.security.AuthUser;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.AgentMeResponse;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.SubscriptionBrief;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.SubscriptionInitResponse;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.SubscriptionView;
import com.assetshield.marketplace.web.dto.PaymentDtos.InitializeRequest;
import com.assetshield.marketplace.web.dto.PaymentDtos.InitializeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentAccountService {

    private final AgentGates gates;
    private final AgentSubscriptionRepository subscriptionRepository;
    private final PaymentService paymentService;
    private final AppProperties properties;

    public AgentAccountService(AgentGates gates, AgentSubscriptionRepository subscriptionRepository,
                               PaymentService paymentService, AppProperties properties) {
        this.gates = gates;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentService = paymentService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public AgentMeResponse me(AuthUser user) {
        InsuranceAgent agent = gates.requireAgentRecord(user);
        SubscriptionBrief brief = subscriptionRepository
                .findFirstByAgentIdOrderByExpiresAtDesc(agent.getId())
                .map(sub -> new SubscriptionBrief(sub.getStatus().name(), sub.getExpiresAt()))
                .orElse(null);
        return new AgentMeResponse(agent.getId(), agent.getInsurerName(), agent.getNicLicenceNo(),
                agent.getVerificationStatus().name(), agent.getRejectionReason(), brief);
    }

    @Transactional(readOnly = true)
    public SubscriptionView subscription(AuthUser user) {
        InsuranceAgent agent = gates.requireVerifiedAgent(user);
        return subscriptionRepository.findFirstByAgentIdOrderByExpiresAtDesc(agent.getId())
                .map(AgentAccountService::view)
                .orElse(SubscriptionView.none());
    }

    private static SubscriptionView view(AgentSubscription sub) {
        return new SubscriptionView(sub.getStatus().name(), sub.getPlan(),
                sub.getStartedAt(), sub.getExpiresAt());
    }

    /** Gate A only: lapsed agents must be able to pay their way back in. */
    @Transactional
    public SubscriptionInitResponse initiateSubscription(AuthUser user) {
        InsuranceAgent agent = gates.requireVerifiedAgent(user);
        InitializeResponse init = paymentService.initialize(new InitializeRequest(
                user.id(), user.phone(), PaymentPurpose.AGENT_SUBSCRIPTION,
                properties.pricing().agentSubGhs(), agent.getId()));
        return new SubscriptionInitResponse(init.paymentId(), init.reference(),
                properties.pricing().agentSubGhs(), "GHS", init.authorizationUrl());
    }
}
