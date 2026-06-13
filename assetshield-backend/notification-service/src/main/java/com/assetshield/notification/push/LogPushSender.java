package com.assetshield.notification.push;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** FCM_MODE=log: records the would-be push at INFO (dev, tests, offline demo). */
public class LogPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(LogPushSender.class);

    @Override
    public PushOutcome send(List<String> tokens, String title, String body, Map<String, String> data) {
        log.info("PUSH to {} device(s): '{}' — {} data={}", tokens.size(), title, body, data);
        return PushOutcome.allSucceeded(tokens.size());
    }
}
