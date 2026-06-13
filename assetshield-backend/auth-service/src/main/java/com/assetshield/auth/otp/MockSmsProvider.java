package com.assetshield.auth.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Dev/demo provider: logs the OTP instead of sending an SMS. */
public class MockSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(MockSmsProvider.class);

    @Override
    public void sendOtp(String phoneNumber, String code) {
        log.info("[MOCK SMS] OTP for {} is {}", phoneNumber, code);
    }
}
