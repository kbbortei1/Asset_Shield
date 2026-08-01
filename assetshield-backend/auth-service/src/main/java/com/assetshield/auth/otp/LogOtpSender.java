package com.assetshield.auth.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dev/test delivery: logs the code instead of emailing it, so the whole flow
 * runs offline with no SMTP. Selected when OTP_CHANNEL != email. Combined with
 * OTP_DEV_CODE this lets the demo verify without any external service.
 */
public class LogOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(LogOtpSender.class);

    @Override
    public void send(String recipient, String code) {
        log.info("OTP (dev channel) for {}: {}", recipient, code);
    }
}
