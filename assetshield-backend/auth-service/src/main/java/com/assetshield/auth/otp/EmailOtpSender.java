package com.assetshield.auth.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Real OTP delivery over SMTP (provider-agnostic — Brevo, SendGrid, Gmail, …,
 * configured via spring.mail.*). Selected when OTP_CHANNEL=email. Chosen over
 * SMS for launch: no telecom sender-ID approval, no per-message cost, instant
 * and reliable.
 */
public class EmailOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(EmailOtpSender.class);

    private final JavaMailSender mailSender;
    private final String from;

    public EmailOtpSender(JavaMailSender mailSender, String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String recipient, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("Your AssetShield GH verification code");
        message.setText("Your AssetShield GH verification code is " + code + ".\n\n"
                + "It expires shortly. Do not share this code with anyone.\n\n"
                + "If you did not request this, you can ignore this email.");
        try {
            mailSender.send(message);
            log.info("OTP email dispatched to {}", recipient);
        } catch (Exception e) {
            // Never log the code; fail clearly so the caller tells the user we
            // couldn't send a code rather than silently proceeding.
            log.error("OTP email to {} failed: {}", recipient, e.getMessage());
            throw new IllegalStateException("Could not send the verification email");
        }
    }
}
