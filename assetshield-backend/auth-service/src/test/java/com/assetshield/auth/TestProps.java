package com.assetshield.auth;

import com.assetshield.auth.config.AppProperties;

public final class TestProps {

    public static final String JWT_SECRET =
            "test-only-jwt-secret-0123456789abcdef0123456789abcdef0123456789abcdef";
    public static final String INTERNAL_API_KEY = "test-internal-key";
    public static final String SUPERADMIN_PHONE = "+233200000099";
    public static final String SUPERADMIN_PASSWORD = "SuperSecret#1";
    public static final String DEV_CODE = "123456";

    private TestProps() {
    }

    public static AppProperties appProperties(long accessTtlSeconds) {
        return new AppProperties(
                new AppProperties.Jwt(JWT_SECRET, accessTtlSeconds),
                new AppProperties.Refresh(14),
                new AppProperties.Otp(300, 3, 60, DEV_CODE),
                new AppProperties.Sms("mock"),
                INTERNAL_API_KEY,
                "http://marketplace-service.invalid",
                "http://notification-service.invalid",
                new AppProperties.Superadmin(SUPERADMIN_PHONE, SUPERADMIN_PASSWORD),
                new AppProperties.Storage("local", "target/test-storage", "", "", "", "",
                        "assetshield", 15));
    }
}
