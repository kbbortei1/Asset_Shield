package com.assetshield.auth.otp;

import com.assetshield.auth.common.ApiException;
import com.assetshield.auth.common.ErrorCode;
import com.assetshield.auth.config.AppProperties;
import com.assetshield.auth.domain.OtpCode;
import com.assetshield.auth.domain.OtpPurpose;
import com.assetshield.auth.repo.OtpCodeRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 6-digit codes, BCrypt-hashed, 5-minute TTL, max 3 verification attempts,
 * resend throttled to one per 60 seconds per phone. When OTP_DEV_CODE is set,
 * that fixed code is also accepted (dev/demo convenience).
 */
@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpCodeRepository repository;
    private final SmsProvider smsProvider;
    private final PasswordEncoder passwordEncoder;
    private final long ttlSeconds;
    private final int maxAttempts;
    private final long resendIntervalSeconds;
    private final String devCode;

    public OtpService(OtpCodeRepository repository, SmsProvider smsProvider,
                      PasswordEncoder passwordEncoder, AppProperties properties) {
        this.repository = repository;
        this.smsProvider = smsProvider;
        this.passwordEncoder = passwordEncoder;
        this.ttlSeconds = properties.otp().ttlSeconds();
        this.maxAttempts = properties.otp().maxAttempts();
        this.resendIntervalSeconds = properties.otp().resendIntervalSeconds();
        this.devCode = properties.otp().devCode();
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    /**
     * noRollbackFor: callers join this transaction; the OTP_THROTTLED 429 must
     * not mark the shared transaction rollback-only.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void issue(String phoneNumber, OtpPurpose purpose) {
        Instant now = Instant.now();
        repository.findTopByPhoneNumberAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(phoneNumber, purpose)
                .filter(latest -> latest.getCreatedAt() != null
                        && latest.getCreatedAt().isAfter(now.minus(Duration.ofSeconds(resendIntervalSeconds))))
                .ifPresent(latest -> {
                    throw new ApiException(ErrorCode.OTP_THROTTLED,
                            "Please wait before requesting another code");
                });

        repository.consumeAllActive(phoneNumber, purpose, now);

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        OtpCode otp = new OtpCode();
        otp.setPhoneNumber(phoneNumber);
        otp.setCodeHash(passwordEncoder.encode(code));
        otp.setPurpose(purpose);
        otp.setExpiresAt(now.plus(Duration.ofSeconds(ttlSeconds)));
        repository.save(otp);

        smsProvider.sendOtp(phoneNumber, code);
    }

    /** Throws on failure; consumes the code on success. */
    @Transactional(noRollbackFor = ApiException.class)
    public void verify(String phoneNumber, OtpPurpose purpose, String code) {
        OtpCode active = repository
                .findTopByPhoneNumberAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(phoneNumber, purpose)
                .orElseThrow(() -> new ApiException(ErrorCode.OTP_INVALID, "Invalid verification code"));

        if (active.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.OTP_EXPIRED, "Verification code has expired");
        }
        if (active.getAttempts() >= maxAttempts) {
            throw new ApiException(ErrorCode.OTP_INVALID, "Invalid verification code");
        }

        boolean devMatch = devCode != null && !devCode.isBlank() && devCode.equals(code);
        if (!devMatch && !passwordEncoder.matches(code, active.getCodeHash())) {
            active.setAttempts((short) (active.getAttempts() + 1));
            repository.save(active);
            throw new ApiException(ErrorCode.OTP_INVALID, "Invalid verification code");
        }

        active.setConsumedAt(Instant.now());
        repository.save(active);
    }
}
