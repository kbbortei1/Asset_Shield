package com.assetshield.marketplace.subscription;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduling shell around {@link SubscriptionService} — separated so tests
 * can drive the transactional job bodies directly.
 */
@Component
public class SubscriptionJobs {

    private final SubscriptionSettlementService settlementService;

    public SubscriptionJobs(SubscriptionSettlementService settlementService) {
        this.settlementService = settlementService;
    }

    /** Hourly expiry sweep. */
    @Scheduled(initialDelay = 30_000, fixedDelay = 3_600_000)
    public void expirySweep() {
        settlementService.expireLapsed();
    }

    /** Daily 08:00 Africa/Accra: 7-day expiry warnings (one per subscription). */
    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Accra")
    public void expiryWarning() {
        settlementService.warnExpiring();
    }
}
