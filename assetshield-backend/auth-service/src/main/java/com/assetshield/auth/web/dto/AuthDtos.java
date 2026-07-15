package com.assetshield.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
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
                                  String language, boolean ghanaCardUploaded, Instant createdAt) {
    }

    public record UpdateProfileRequest(
            @Size(min = 2, max = 120) String fullName,
            @Pattern(regexp = "^(en|tw)$", message = "must be 'en' or 'tw'") String language) {
    }

    public record PurgeResponse(boolean purgeScheduled, Instant deadline) {
    }

    public record GhanaCardResponse(boolean ghanaCardUploaded) {
    }

    public record CreateAdminRequest(
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phoneNumber,
            @NotBlank @Size(min = 8, max = 72, message = "must be at least 8 characters") String password,
            @NotBlank @Size(min = 2, max = 120) String fullName) {
    }

    public record CreateAdminResponse(UUID userId) {
    }

    public record InternalUserResponse(UUID id, String fullName, String phoneNumber,
                                       String role, String status) {
    }
}
