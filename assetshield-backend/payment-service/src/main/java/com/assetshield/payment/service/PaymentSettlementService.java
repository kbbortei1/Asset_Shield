package com.assetshield.payment.service;

import com.assetshield.payment.domain.Payment;
import com.assetshield.payment.domain.PaymentStatus;
import com.assetshield.payment.repo.PaymentRepository;
import com.assetshield.payment.client.DamageServiceClient;
import com.assetshield.payment.client.MarketplaceServiceClient;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

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
    private final MarketplaceServiceClient marketplaceServiceClient;
    private final ObjectMapper objectMapper;

    public PaymentSettlementService(PaymentRepository paymentRepository,
                                    DamageServiceClient damageServiceClient,
                                    MarketplaceServiceClient marketplaceServiceClient,
                                    ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.damageServiceClient = damageServiceClient;
        this.marketplaceServiceClient = marketplaceServiceClient;
        this.objectMapper = objectMapper;
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
        payment.setRawWebhook(jsonOrNull(rawPayload));
        paymentRepository.saveAndFlush(payment);
        log.info("Payment {} ({}) settled SUCCESS", reference, payment.getPurpose());

        dispatch(payment);
    }

    /**
     * raw_webhook is JSONB: a non-JSON payload makes Postgres abort the whole
     * settle transaction, which would lose a real payment over an audit field.
     * Money wins — keep the settlement, drop the unparseable blob.
     */
    private String jsonOrNull(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return null;
        }
        try {
            objectMapper.readTree(rawPayload);
            return rawPayload;
        } catch (Exception e) {
            log.warn("Provider payload was not valid JSON — settling without the audit blob: {}",
                    e.getMessage());
            return null;
        }
    }

    /** Dispatch failures are logged, never thrown — the reconciler picks them up. */
    private void dispatch(Payment payment) {
        try {
            switch (payment.getPurpose()) {
                case DOSSIER_FEE -> damageServiceClient.dossierPaymentConfirmed(
                        payment.getReferenceEntityId(), payment.getId());
                case AGENT_SUBSCRIPTION, PRO_SUBSCRIPTION ->
                        marketplaceServiceClient.subscriptionPaymentConfirmed(
                                payment.getPurpose().name(), payment.getReferenceEntityId(), payment.getId());
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
