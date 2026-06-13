package com.assetshield.auth.otp;

/**
 * SMS delivery abstraction. Only {@link MockSmsProvider} exists today; a real
 * Ghanaian provider (e.g. Hubtel, Arkesel) drops in later without refactoring.
 */
public interface SmsProvider {

    void sendOtp(String phoneNumber, String code);
}
