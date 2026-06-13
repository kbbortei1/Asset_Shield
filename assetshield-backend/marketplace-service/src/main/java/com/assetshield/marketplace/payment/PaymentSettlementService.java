package com.assetshield.marketplace.payment;

import com.assetshield.marketplace.domain.Payment;
import com.assetshield.marketplace.domain.PaymentStatus;
import com.assetshield.marketplace.repo.PaymentRepository;
import com.assetshield.marketplace.subscription.SubscriptionSettlementService;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single, idempotent success-handling pipeline — both the webhook and the
 * client-driven verify endpoint land here. A payment settles exactly once;
 * replays are no-ops. Downstream dispatch failures never lose the settlement:
 * dispatched_at stays NULL and the reconciler retries every 60 seconds.
 */
@Service
public class PaymentSettlementService {

    private static final Logger log = LoggerFactory.getLogger(PaymentSettlementService.class);

    private final PaymentRepository paymentRepository;
    private final DamageServiceClient damageServiceClient;
    private final SubscriptionSettlementService subscriptionSettlementService;

    public PaymentSettlementService(PaymentRepository paymentRepository,
                                    DamageServiceClient damageServiceClient,
                                    SubscriptionSettlementService subscriptionSettlementService) {
        this.paymentRepository = paymentRepository;
        this.damageServiceClient = damageServiceClient;
        this.subscriptionSettlementService = subscriptionSettlementService;
    }

    @Transactional
    public void settle(String reference, String rawPayload) {
        Optional<Payment> found = paymentRepository.findByProviderReference(reference);
        if (found.isEmpty()) {
            log.warn("Settlement for unknown reference {} — acknowledged and ignored", reference);
            return;
        }
        Payment payment = found.get();
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            log.info("Replayed settlement for {} — idempotent no-op", reference);
            return;
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setWebhookReceivedAt(Instant.now());
        payment.setRawWebhook(rawPayload);
        paymentRepository.saveAndFlush(payment);
        log.info("Payment {} ({}) settled SUCCESS", reference, payment.getPurpose());

        dispatch(payment);
    }

    /** Dispatch failures are logged, never thrown — the reconciler picks them up. */
    private void dispatch(Payment payment) {
        try {
            switch (payment.getPurpose()) {
                case DOSSIER_FEE -> damageServiceClient.dossierPaymentConfirmed(
                        payment.getReferenceEntityId(), payment.getId());
                case AGENT_SUBSCRIPTION ->
                        subscriptionSettlementService.activateAgentSubscription(payment);
                case PRO_SUBSCRIPTION ->
                        subscriptionSettlementService.activateProSubscription(payment);
            }
            payment.setDispatchedAt(Instant.now());
            paymentRepository.saveAndFlush(payment);
        } catch (Exception e) {
            log.error("Downstream dispatch failed for {} ({}): {} — reconciler will retry",
                    payment.getProviderReference(), payment.getPurpose(), e.getMessage());
        }
    }

    /** Re-dispatches settled payments whose downstream never acknowledged. */
    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    @Transactional
    public void reconcile() {
        for (Payment payment : paymentRepository.findByStatusAndDispatchedAtIsNull(PaymentStatus.SUCCESS)) {
            log.info("Reconciler re-dispatching {} ({})",
                    payment.getProviderReference(), payment.getPurpose());
            dispatch(payment);
        }
    }
}
