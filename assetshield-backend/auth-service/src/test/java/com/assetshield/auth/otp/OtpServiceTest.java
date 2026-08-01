package com.assetshield.auth.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetshield.auth.TestProps;
import com.assetshield.auth.common.ApiException;
import com.assetshield.auth.common.ErrorCode;
import com.assetshield.auth.domain.OtpCode;
import com.assetshield.auth.domain.OtpPurpose;
import com.assetshield.auth.repo.OtpCodeRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    private static final String PHONE = "+233200000001";
    private static final String RECIPIENT = "trader@example.com";

    @Mock
    OtpCodeRepository repository;

    @Mock
    OtpSender otpSender;

    PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    OtpService service;

    @BeforeEach
    void setUp() {
        service = new OtpService(repository, otpSender, encoder, TestProps.appProperties(3600));
    }

    private OtpCode activeCode(String code) {
        OtpCode otp = new OtpCode();
        otp.setPhoneNumber(PHONE);
        otp.setPurpose(OtpPurpose.REGISTRATION);
        otp.setCodeHash(encoder.encode(code));
        otp.setExpiresAt(Instant.now().plus(Duration.ofMinutes(5)));
        otp.setCreatedAt(Instant.now());
        return otp;
    }

    @Test
    void issueSendsSixDigitCodeAndStoresOnlyTheHash() {
        when(repository.findTopByPhoneNumberAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                PHONE, OtpPurpose.REGISTRATION)).thenReturn(Optional.empty());

        service.issue(PHONE, OtpPurpose.REGISTRATION, RECIPIENT);

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(otpSender).send(eq(RECIPIENT), codeCaptor.capture());
        assertThat(codeCaptor.getValue()).matches("\\d{6}");

        ArgumentCaptor<OtpCode> otpCaptor = ArgumentCaptor.forClass(OtpCode.class);
        verify(repository).save(otpCaptor.capture());
        assertThat(otpCaptor.getValue().getCodeHash()).isNotEqualTo(codeCaptor.getValue());
        assertThat(encoder.matches(codeCaptor.getValue(), otpCaptor.getValue().getCodeHash())).isTrue();
        verify(repository).consumeAllActive(eq(PHONE), eq(OtpPurpose.REGISTRATION), any());
    }

    @Test
    void issueWithin60SecondsOfPreviousCodeIsThrottled() {
        OtpCode recent = activeCode("111111");
        recent.setCreatedAt(Instant.now().minusSeconds(10));
        when(repository.findTopByPhoneNumberAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                PHONE, OtpPurpose.REGISTRATION)).thenReturn(Optional.of(recent));

        assertThatThrownBy(() -> service.issue(PHONE, OtpPurpose.REGISTRATION, RECIPIENT))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.OTP_THROTTLED));
        verify(otpSender, never()).send(any(), any());
    }

    @Test
    void issueAfterThrottleWindowConsumesPreviousCodes() {
        OtpCode old = activeCode("111111");
        old.setCreatedAt(Instant.now().minusSeconds(90));
        when(repository.findTopByPhoneNumberAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                PHONE, OtpPurpose.REGISTRATION)).thenReturn(Optional.of(old));

        assertThatCode(() -> service.issue(PHONE, OtpPurpose.REGISTRATION, RECIPIENT)).doesNotThrowAnyException();
        verify(repository).consumeAllActive(eq(PHONE), eq(OtpPurpose.REGISTRATION), any());
        verify(otpSender).send(eq(RECIPIENT), any());
    }

    @Test
    void correctCodeVerifiesAndIsConsumed() {
        OtpCode active = activeCode("654321");
        when(repository.findTopByPhoneNumberAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                PHONE, OtpPurpose.REGISTRATION)).thenReturn(Optional.of(active));

        service.verify(PHONE, OtpPurpose.REGISTRATION, "654321");

        assertThat(active.getConsumedAt()).isNotNull();
        verify(repository).save(active);
    }

    @Test
    void devCodeIsAcceptedEvenThoughItDoesNotMatchTheHash() {
        OtpCode active = activeCode("654321");
        when(repository.findTopByPhoneNumberAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                PHONE, OtpPurpose.REGISTRATION)).thenReturn(Optional.of(active));

        service.verify(PHONE, OtpPurpose.REGISTRATION, TestProps.DEV_CODE);

        assertThat(active.getConsumedAt()).isNotNull();
    }

    @Test
    void expiredCodeIsRejectedWithOtpExpired() {
        OtpCode expired = activeCode("654321");
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        when(repository.findTopByPhoneNumberAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                PHONE, OtpPurpose.REGISTRATION)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.verify(PHONE, OtpPurpose.REGISTRATION, "654321"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.OTP_EXPIRED));
    }

    @Test
    void wrongCodeIncrementsAttemptsAndCapsAtThree() {
        OtpCode active = activeCode("654321");
        when(repository.findTopByPhoneNumberAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                PHONE, OtpPurpose.REGISTRATION)).thenReturn(Optional.of(active));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.verify(PHONE, OtpPurpose.REGISTRATION, "000000"))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.OTP_INVALID));
        }
        assertThat(active.getAttempts()).isEqualTo((short) 3);

        // Attempts exhausted: even the CORRECT code is now rejected.
        assertThatThrownBy(() -> service.verify(PHONE, OtpPurpose.REGISTRATION, "654321"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.OTP_INVALID));
        assertThat(active.getConsumedAt()).isNull();
    }

    @Test
    void verifyWithNoActiveCodeIsRejected() {
        when(repository.findTopByPhoneNumberAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                PHONE, OtpPurpose.REGISTRATION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(PHONE, OtpPurpose.REGISTRATION, "654321"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.OTP_INVALID));
    }
}
