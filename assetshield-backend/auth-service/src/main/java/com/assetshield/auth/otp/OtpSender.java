package com.assetshield.auth.otp;

/**
 * Delivers a one-time code to a recipient (an email address today; a phone
 * number if the SMS channel is re-enabled later). The OTP itself is still
 * generated, stored and verified by {@link OtpService} — this only carries it.
 */
public interface OtpSender {

    void send(String recipient, String code);
}
