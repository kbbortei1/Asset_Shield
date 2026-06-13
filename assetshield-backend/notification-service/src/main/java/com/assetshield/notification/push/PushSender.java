package com.assetshield.notification.push;

import java.util.List;
import java.util.Map;

/**
 * FCM dispatch abstraction, env FCM_MODE=firebase|log. Implementations never
 * touch the database — invalid tokens are reported back and revoked by the
 * dispatch pipeline (token hygiene in one place).
 */
public interface PushSender {

    /**
     * @param invalidTokens tokens FCM declared dead (UNREGISTERED /
     *                      INVALID_ARGUMENT) — the caller revokes them
     */
    record PushOutcome(int successCount, int failureCount, List<String> invalidTokens) {

        public static PushOutcome allSucceeded(int count) {
            return new PushOutcome(count, 0, List.of());
        }
    }

    PushOutcome send(List<String> tokens, String title, String body, Map<String, String> data);
}
