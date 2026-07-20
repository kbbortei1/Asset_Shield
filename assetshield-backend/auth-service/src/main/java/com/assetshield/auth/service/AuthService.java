package com.assetshield.auth.service;

import com.assetshield.auth.common.ApiException;
import com.assetshield.auth.common.ErrorCode;
import com.assetshield.auth.domain.OtpPurpose;
import com.assetshield.auth.domain.PendingAgentDetails;
import com.assetshield.auth.domain.Role;
import com.assetshield.auth.domain.User;
import com.assetshield.auth.domain.UserStatus;
import com.assetshield.auth.otp.OtpService;
import com.assetshield.auth.repo.PendingAgentDetailsRepository;
import com.assetshield.auth.repo.UserRepository;
import com.assetshield.auth.token.RefreshTokenService;
import com.assetshield.auth.token.TokenService;
import com.assetshield.auth.web.dto.AuthDtos.AuthTokensResponse;
import com.assetshield.auth.web.dto.AuthDtos.ForgotPasswordRequest;
import com.assetshield.auth.web.dto.AuthDtos.ForgotPasswordResponse;
import com.assetshield.auth.web.dto.AuthDtos.LoginRequest;
import com.assetshield.auth.web.dto.AuthDtos.LogoutRequest;
import com.assetshield.auth.web.dto.AuthDtos.RefreshRequest;
import com.assetshield.auth.web.dto.AuthDtos.RefreshResponse;
import com.assetshield.auth.web.dto.AuthDtos.RegisterAgentRequest;
import com.assetshield.auth.web.dto.AuthDtos.RegisterRequest;
import com.assetshield.auth.web.dto.AuthDtos.RegisterResponse;
import com.assetshield.auth.web.dto.AuthDtos.ResendOtpRequest;
import com.assetshield.auth.web.dto.AuthDtos.ResendOtpResponse;
import com.assetshield.auth.web.dto.AuthDtos.ResetPasswordRequest;
import com.assetshield.auth.web.dto.AuthDtos.UserSummary;
import com.assetshield.auth.web.dto.AuthDtos.VerifyOtpRequest;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PendingAgentDetailsRepository pendingAgentRepository;
    private final OtpService otpService;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final AgentSyncService agentSyncService;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository,
                       PendingAgentDetailsRepository pendingAgentRepository,
                       OtpService otpService,
                       TokenService tokenService,
                       RefreshTokenService refreshTokenService,
                       PasswordEncoder passwordEncoder,
                       AgentSyncService agentSyncService,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.pendingAgentRepository = pendingAgentRepository;
        this.otpService = otpService;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.agentSyncService = agentSyncService;
        this.auditService = auditService;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public RegisterResponse register(RegisterRequest request) {
        User user = upsertPendingUser(request.phoneNumber(), request.password(),
                request.fullName(), Role.OWNER);
        otpService.issue(user.getPhoneNumber(), OtpPurpose.REGISTRATION);
        return new RegisterResponse(user.getId(), true, otpService.ttlSeconds());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public RegisterResponse registerAgent(RegisterAgentRequest request) {
        // Licence uniqueness is enforced properly by Marketplace on Day 5;
        // today we only reject duplicates within this holding table.
        pendingAgentRepository.findByNicLicenceNo(request.nicLicenceNo().trim())
                .filter(existing -> userRepository.findByPhoneNumberAndDeletedAtIsNull(request.phoneNumber())
                        .map(u -> !existing.getUserId().equals(u.getId()))
                        .orElse(true))
                .ifPresent(existing -> {
                    throw new ApiException(ErrorCode.LICENCE_EXISTS, "NIC licence number is already registered");
                });

        User user = upsertPendingUser(request.phoneNumber(), request.password(),
                request.fullName(), Role.AGENT);

        PendingAgentDetails details = pendingAgentRepository.findByUserId(user.getId())
                .orElseGet(PendingAgentDetails::new);
        details.setUserId(user.getId());
        details.setInsurerName(request.insurerName().trim());
        details.setNicLicenceNo(request.nicLicenceNo().trim());
        pendingAgentRepository.save(details);

        otpService.issue(user.getPhoneNumber(), OtpPurpose.REGISTRATION);
        return new RegisterResponse(user.getId(), true, otpService.ttlSeconds());
    }

    private User upsertPendingUser(String phoneNumber, String rawPassword, String fullName, Role role) {
        User user = userRepository.findByPhoneNumberAndDeletedAtIsNull(phoneNumber).orElse(null);
        if (user != null) {
            if (user.getStatus() != UserStatus.PENDING_OTP) {
                throw new ApiException(ErrorCode.PHONE_EXISTS, "Phone number is already registered");
            }
            // Re-registration before verification: refresh name/password, re-issue OTP.
            user.setFullName(fullName.trim());
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            return userRepository.save(user);
        }
        User created = new User();
        created.setPhoneNumber(phoneNumber);
        created.setPasswordHash(passwordEncoder.encode(rawPassword));
        created.setFullName(fullName.trim());
        created.setRole(role);
        created.setStatus(UserStatus.PENDING_OTP);
        return userRepository.save(created);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthTokensResponse verifyOtp(VerifyOtpRequest request) {
        User user = userRepository.findByPhoneNumberAndDeletedAtIsNull(request.phoneNumber())
                .filter(u -> u.getStatus() == UserStatus.PENDING_OTP)
                .orElseThrow(() -> new ApiException(ErrorCode.OTP_INVALID, "Invalid verification code"));

        otpService.verify(request.phoneNumber(), OtpPurpose.REGISTRATION, request.code());

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        if (user.getRole() == Role.AGENT) {
            // best-effort; the 60 s re-push job covers marketplace downtime
            agentSyncService.pushAfterVerification(user.getId());
        }
        auditService.record(user.getId(), AuditService.ACCOUNT_VERIFIED, user.getPhoneNumber(),
                user.getRole().name() + " account activated");
        return issueTokens(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public ResendOtpResponse resendOtp(ResendOtpRequest request) {
        userRepository.findByPhoneNumberAndDeletedAtIsNull(request.phoneNumber())
                .filter(u -> u.getStatus() == UserStatus.PENDING_OTP)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "No pending verification for this phone"));
        otpService.issue(request.phoneNumber(), OtpPurpose.REGISTRATION);
        return new ResendOtpResponse(true, otpService.ttlSeconds());
    }

    @Transactional
    public AuthTokensResponse login(LoginRequest request) {
        User user = userRepository.findByPhoneNumberAndDeletedAtIsNull(request.phoneNumber())
                .orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // REQUIRES_NEW inside AuditService: the row survives this rollback
            auditService.record(user == null ? null : user.getId(), AuditService.LOGIN_FAILED,
                    request.phoneNumber(), "Bad credentials");
            throw badCredentials();
        }
        if (user.getStatus() == UserStatus.PENDING_OTP) {
            throw new ApiException(ErrorCode.OTP_REQUIRED, "Phone number not verified yet");
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            auditService.record(user.getId(), AuditService.LOGIN_FAILED,
                    request.phoneNumber(), "Suspended account");
            throw new ApiException(ErrorCode.FORBIDDEN, "Account is suspended");
        }
        auditService.record(user.getId(), AuditService.LOGIN_SUCCESS, request.phoneNumber(),
                user.getRole().name());
        return issueTokens(user);
    }

    private static ApiException badCredentials() {
        // Identical message whether the phone is unknown or the password is wrong.
        return new ApiException(ErrorCode.BAD_CREDENTIALS, "Invalid phone number or password");
    }

    /**
     * Sends a LOGIN_RECOVERY code to the phone if (and only if) it belongs to an
     * ACTIVE account. The response is identical either way, so the endpoint can't
     * be used to enumerate registered numbers. OTP_THROTTLED still surfaces (the
     * resend-interval guard); enumeration via timing is mitigated at the gateway
     * by per-IP rate limiting.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByPhoneNumberAndDeletedAtIsNull(request.phoneNumber())
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .ifPresent(user -> otpService.issue(user.getPhoneNumber(), OtpPurpose.LOGIN_RECOVERY));
        return new ForgotPasswordResponse(true, otpService.ttlSeconds());
    }

    /**
     * Verifies the LOGIN_RECOVERY code, sets the new password and revokes every
     * refresh token (all sessions must re-login with the new password).
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void resetPassword(ResetPasswordRequest request) {
        otpService.verify(request.phoneNumber(), OtpPurpose.LOGIN_RECOVERY, request.code());
        User user = userRepository.findByPhoneNumberAndDeletedAtIsNull(request.phoneNumber())
                .orElseThrow(() -> new ApiException(ErrorCode.OTP_INVALID, "Code is invalid"));
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());
        auditService.record(user.getId(), AuditService.PASSWORD_RESET, user.getPhoneNumber(),
                "All sessions revoked");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public RefreshResponse refresh(RefreshRequest request) {
        RefreshTokenService.RotatedToken rotated = refreshTokenService.rotate(request.refreshToken());
        User user = userRepository.findByIdAndDeletedAtIsNull(rotated.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.REFRESH_INVALID, "Refresh token is invalid"));
        return new RefreshResponse(tokenService.issueAccessToken(user), rotated.raw(),
                tokenService.accessTtlSeconds());
    }

    @Transactional
    public void logout(UUID callerId, LogoutRequest request) {
        refreshTokenService.revokeFamilyOf(request.refreshToken(), callerId);
    }

    private AuthTokensResponse issueTokens(User user) {
        String accessToken = tokenService.issueAccessToken(user);
        String refreshToken = refreshTokenService.issue(user.getId(), UUID.randomUUID()).raw();
        return new AuthTokensResponse(accessToken, refreshToken, tokenService.accessTtlSeconds(),
                new UserSummary(user.getId(), user.getFullName(), user.getRole().name(), user.getLanguage()));
    }
}
