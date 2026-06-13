package com.assetshield.marketplace.subscription;

import com.assetshield.marketplace.client.NotificationClient;
import com.assetshield.marketplace.domain.AgentSubscription;
import com.assetshield.marketplace.domain.Payment;
import com.assetshield.marketplace.domain.SubscriptionStatus;
import com.assetshield.marketplace.domain.UserSubscription;
import com.assetshield.marketplace.repo.AgentSubscriptionRepository;
import com.assetshield.marketplace.repo.InsuranceAgentRepository;
import com.assetshield.marketplace.repo.UserSubscriptionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Subscription state transitions that do not initiate payments: settlement
 * activation (the Day 4 settle() extension points) and the expiry jobs.
 * Kept separate from {@link SubscriptionService} so the dependency chain
 * PaymentService → PaymentSettlementService → here stays acyclic.
 */
@Service
public class SubscriptionSettlementService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionSettlementService.class);
    static final Duration TERM = Duration.ofDays(30);

    private final AgentSubscriptionRepository agentSubscriptionRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final InsuranceAgentRepository agentRepository;
    private final NotificationClient notificationClient;

    public SubscriptionSettlementService(AgentSubscriptionRepository agentSubscriptionRepository,
                                         UserSubscriptionRepository userSubscriptionRepository,
                                         InsuranceAgentRepository agentRepository,
                                         NotificationClient notificationClient) {
        this.agentSubscriptionRepository = agentSubscriptionRepository;
        this.userSubscriptionRepository = userSubscriptionRepository;
        this.agentRepository = agentRepository;
        this.notificationClient = notificationClient;
    }

    /**
     * AGENT_SUBSCRIPTION success: extend the ACTIVE subscription by 30 days
     * (renewal extends from the current expiry, never truncates) or create a
     * fresh one. Re-dispatch of the same payment is a no-op (last_payment_id).
     */
    @Transactional
    public void activateAgentSubscription(Payment payment) {
        UUID agentId = payment.getReferenceEntityId();
        AgentSubscription sub = agentSubscriptionRepository
                .findByAgentIdAndStatus(agentId, SubscriptionStatus.ACTIVE)
                .orElse(null);
        if (sub != null) {
            if (payment.getId().equals(sub.getLastPaymentId())) {
                return; // this payment is already applied
            }
            sub.setExpiresAt(sub.getExpiresAt().plus(TERM));
            sub.setExpiryWarnedAt(null);
        } else {
            sub = new AgentSubscription();
            sub.setAgentId(agentId);
            sub.setStatus(SubscriptionStatus.ACTIVE);
            sub.setStartedAt(Instant.now());
            sub.setExpiresAt(Instant.now().plus(TERM));
        }
        sub.setLastPaymentId(payment.getId());
        agentSubscriptionRepository.save(sub);
        // activation is visible in the app immediately — no push type exists
        // for it in the Day 6 vocabulary, so an INFO line is the record
        log.info("Agent subscription active: agent={} expiresAt={}", agentId, sub.getExpiresAt());
    }

    /** PRO_SUBSCRIPTION success: same extend-or-create rules on user_subscriptions. */
    @Transactional
    public void activateProSubscription(Payment payment) {
        UUID userId = payment.getReferenceEntityId();
        UserSubscription sub = userSubscriptionRepository
                .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .orElse(null);
        if (sub != null) {
            if (payment.getId().equals(sub.getLastPaymentId())) {
                return; // this payment is already applied
            }
            sub.setExpiresAt(sub.getExpiresAt().plus(TERM));
            sub.setExpiryWarnedAt(null);
        } else {
            sub = new UserSubscription();
            sub.setUserId(userId);
            sub.setStatus(SubscriptionStatus.ACTIVE);
            sub.setStartedAt(Instant.now());
            sub.setExpiresAt(Instant.now().plus(TERM));
        }
        sub.setLastPaymentId(payment.getId());
        userSubscriptionRepository.save(sub);
        // activation is visible in the app immediately — no push type exists
        // for it in the Day 6 vocabulary, so an INFO line is the record
        log.info("PRO subscription active: user={} expiresAt={}", userId, sub.getExpiresAt());
    }

    /** Hourly: flip lapsed ACTIVE rows (both tables) to EXPIRED. */
    @Transactional
    public void expireLapsed() {
        Instant now = Instant.now();
        for (AgentSubscription sub : agentSubscriptionRepository
                .findByStatusAndExpiresAtLessThanEqual(SubscriptionStatus.ACTIVE, now)) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            agentSubscriptionRepository.save(sub);
            agentRepository.findById(sub.getAgentId()).ifPresent(agent ->
                    notificationClient.send(agent.getUserId(), "SUBSCRIPTION_EXPIRY",
                            "Your marketplace subscription expired",
                            "Renew to regain access to leads and shared dossiers.",
                            Map.of("subscriptionId", sub.getId().toString())));
        }
        for (UserSubscription sub : userSubscriptionRepository
                .findByStatusAndExpiresAtLessThanEqual(SubscriptionStatus.ACTIVE, now)) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            userSubscriptionRepository.save(sub);
            notificationClient.send(sub.getUserId(), "SUBSCRIPTION_EXPIRY",
                    "Your PRO subscription expired",
                    "Your account is back on the FREE tier.",
                    Map.of("subscriptionId", sub.getId().toString()));
        }
    }

    /** Daily: warn subscriptions expiring within 7 days — at most once each. */
    @Transactional
    public void warnExpiring() {
        Instant now = Instant.now();
        Instant horizon = now.plus(Duration.ofDays(7));
        for (AgentSubscription sub : agentSubscriptionRepository
                .findByStatusAndExpiresAtBetweenAndExpiryWarnedAtIsNull(
                        SubscriptionStatus.ACTIVE, now, horizon)) {
            sub.setExpiryWarnedAt(now);
            agentSubscriptionRepository.save(sub);
            agentRepository.findById(sub.getAgentId()).ifPresent(agent ->
                    notificationClient.send(agent.getUserId(), "SUBSCRIPTION_EXPIRY",
                            "Your marketplace subscription expires soon",
                            "Expires " + sub.getExpiresAt() + ". Renew to keep access.",
                            Map.of("expiresAt", sub.getExpiresAt().toString())));
        }
        for (UserSubscription sub : userSubscriptionRepository
                .findByStatusAndExpiresAtBetweenAndExpiryWarnedAtIsNull(
                        SubscriptionStatus.ACTIVE, now, horizon)) {
            sub.setExpiryWarnedAt(now);
            userSubscriptionRepository.save(sub);
            notificationClient.send(sub.getUserId(), "SUBSCRIPTION_EXPIRY",
                    "Your PRO subscription expires soon",
                    "Expires " + sub.getExpiresAt() + ". Renew to keep PRO limits.",
                    Map.of("expiresAt", sub.getExpiresAt().toString()));
        }
    }
}
