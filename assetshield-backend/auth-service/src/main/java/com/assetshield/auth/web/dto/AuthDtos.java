package com.assetshield.auth.web.dto;

import com.assetshield.auth.domain.BroadcastAudience;
import com.assetshield.auth.domain.ReportCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AuthDtos {

    public static final String PHONE_REGEX = "^\\+233\\d{9}$";
    public static final String PHONE_MESSAGE = "must match +233XXXXXXXXX";

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phoneNumber,
            @NotBlank @Size(min = 8, max = 72, message = "must be at least 8 characters") String password,
            @NotBlank @Size(min = 2, max = 120) String fullName) {
    }

    public record RegisterAgentRequest(
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phoneNumber,
            @NotBlank @Size(min = 8, max = 72, message = "must be at least 8 characters") String password,
            @NotBlank @Size(min = 2, max = 120) String fullName,
            @NotBlank @Size(max = 120) String insurerName,
            @NotBlank @Size(max = 50) String nicLicenceNo) {
    }

    public record RegisterResponse(UUID userId, boolean otpSent, long expiresInSeconds) {
    }

    public record VerifyOtpRequest(
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phoneNumber,
            @NotBlank String code) {
    }

    public record ResendOtpRequest(
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phoneNumber) {
    }

    public record ResendOtpResponse(boolean otpSent, long expiresInSeconds) {
    }

    public record LoginRequest(
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phoneNumber,
            @NotBlank String password) {
    }

    public record ForgotPasswordRequest(
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phoneNumber) {
    }

    public record ForgotPasswordResponse(boolean otpSent, long expiresInSeconds) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phoneNumber,
            @NotBlank String code,
            @NotBlank @Size(min = 8, max = 72, message = "must be at least 8 characters") String newPassword) {
    }

    public record UserSummary(UUID id, String fullName, String role, String language) {
    }

    public record AuthTokensResponse(String accessToken, String refreshToken,
                                     long expiresInSeconds, UserSummary user) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record RefreshResponse(String accessToken, String refreshToken, long expiresInSeconds) {
    }

    public record LogoutRequest(@NotBlank String refreshToken) {
    }

    public record LogoutResponse(boolean loggedOut) {
    }

    public record ProfileResponse(UUID id, String phoneNumber, String fullName, String role,
                                  String language, boolean ghanaCardUploaded, String avatarUrl,
                                  Instant createdAt) {
    }

    public record UpdateProfileRequest(
            @Size(min = 2, max = 120) String fullName,
            @Pattern(regexp = "^(en|tw)$", message = "must be 'en' or 'tw'") String language) {
    }

    public record PurgeResponse(boolean purgeScheduled, Instant deadline) {
    }

    public record GhanaCardResponse(boolean ghanaCardUploaded) {
    }

    public record VerifyPasswordRequest(@NotBlank String password) {
    }

    /** Always 200; `verified` is the answer — never leaks which field was wrong. */
    public record VerifyPasswordResponse(boolean verified) {
    }

    public record CreateAdminRequest(
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phoneNumber,
            @NotBlank @Size(min = 8, max = 72, message = "must be at least 8 characters") String password,
            @NotBlank @Size(min = 2, max = 120) String fullName) {
    }

    public record CreateAdminResponse(UUID userId) {
    }

    /** One security audit row; actorUserId is null for unknown-phone failures. */
    public record AuditEventItem(UUID id, UUID actorUserId, String action, String target,
                                 String detail, Instant createdAt) {
    }

    // ── problem reports (support) ────────────────────────────────────────────

    public record CreateReportRequest(
            @NotNull ReportCategory category,
            @NotBlank @Size(min = 5, max = 2000) String message,
            @Size(max = 200) String context) {
    }

    public record ReportResponse(UUID id, String status) {
    }

    /** Admin list row — reporter name/phone resolved from the local users table. */
    public record ReportItem(UUID id, ReportCategory category, String message, String context,
                             String status, UUID reporterUserId, String reporterName,
                             String reporterPhone, Instant createdAt, Instant resolvedAt) {
    }

    // ── admin broadcasts ─────────────────────────────────────────────────────

    /** Live reach per segment, so the composer can preview before sending. */
    public record AudienceCountsResponse(long everyone, long owners, long agents) {
    }

    /** One row in the "specific people" directory picker. */
    public record AdminUserItem(UUID id, String fullName, String phoneNumber, String role) {
    }

    /** userIds is required only when audience = SPECIFIC. */
    public record BroadcastRequest(
            @NotNull BroadcastAudience audience,
            List<UUID> userIds,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 500) String body) {
    }

    public record BroadcastResponse(boolean sent, int recipientCount) {
    }

    public record InternalUserResponse(UUID id, String fullName, String phoneNumber,
                                       String role, String status) {
    }
}
