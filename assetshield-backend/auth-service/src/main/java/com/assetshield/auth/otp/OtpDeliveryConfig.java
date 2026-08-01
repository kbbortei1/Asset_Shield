package com.assetshield.auth.otp;

import com.assetshield.auth.config.AppProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Chooses how OTPs are delivered: real email (OTP_CHANNEL=email) or a dev log
 * (anything else, the default for local/tests). JavaMailSender is optional so
 * the log channel needs no SMTP configured at all.
 */
@Configuration
public class OtpDeliveryConfig {

    @Bean
    public OtpSender otpSender(AppProperties properties, ObjectProvider<JavaMailSender> mailSender) {
        AppProperties.Otp otp = properties.otp();
        if ("email".equalsIgnoreCase(otp.channel())) {
            JavaMailSender sender = mailSender.getIfAvailable();
            if (sender == null) {
                throw new IllegalStateException(
                        "OTP_CHANNEL=email requires SMTP to be configured (set MAIL_HOST / spring.mail.*)");
            }
            return new EmailOtpSender(sender, otp.fromEmail());
        }
        return new LogOtpSender();
    }
}
