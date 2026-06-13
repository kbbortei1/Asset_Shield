package com.assetshield.auth.otp;

import com.assetshield.auth.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SmsConfig {

    @Bean
    public SmsProvider smsProvider(AppProperties properties) {
        String provider = properties.sms().provider();
        if ("mock".equalsIgnoreCase(provider)) {
            return new MockSmsProvider();
        }
        throw new IllegalStateException("Unsupported SMS_PROVIDER: " + provider + " (only 'mock' is implemented)");
    }
}
