package com.assetshield.marketplace.payment;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

/**
 * PAYMENTS_MODE=mock (dev/offline demo): initialize returns a fake checkout
 * URL and — after a short delay simulating instant MoMo confirmation — runs
 * the real settlement pipeline, so the full dossier flow demos with zero
 * internet. Verify always reports SUCCESS. A delay &lt; 0 disables
 * auto-settlement (tests drive the webhook explicitly instead).
 */
public class MockProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(MockProvider.class);

    private final ObjectProvider<PaymentSettlementService> settlementService;
    private final long autoSettleMs;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "mock-payment-settler");
                thread.setDaemon(true);
                return thread;
            });

    public MockProvider(ObjectProvider<PaymentSettlementService> settlementService, long autoSettleMs) {
        this.settlementService = settlementService;
        this.autoSettleMs = autoSettleMs;
    }

    @Override
    public InitResult initialize(String reference, BigDecimal amountGhs, String email,
                                 Map<String, Object> metadata) {
        if (autoSettleMs >= 0) {
            scheduler.schedule(() -> {
                try {
                    log.info("MOCK payment auto-settling {} after {} ms", reference, autoSettleMs);
                    settlementService.getObject().settle(reference,
                            "{\"event\":\"charge.success\",\"mock\":true,\"reference\":\"" + reference + "\"}");
                } catch (Exception e) {
                    log.error("Mock auto-settle failed for {}", reference, e);
                }
            }, autoSettleMs, TimeUnit.MILLISECONDS);
        }
        return new InitResult("http://localhost:8080/mock-checkout/" + reference, "mock-" + reference);
    }

    @Override
    public VerifyResult verify(String reference) {
        return new VerifyResult(VerifyStatus.SUCCESS,
                "{\"mock\":true,\"reference\":\"" + reference + "\",\"status\":\"success\"}");
    }
}
