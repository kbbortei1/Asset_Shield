package com.assetshield.auth.web;

import com.assetshield.auth.common.ApiResponse;
import com.assetshield.auth.service.AuthService;
import com.assetshield.auth.web.dto.AuthDtos.AuthTokensResponse;
import com.assetshield.auth.web.dto.AuthDtos.ForgotPasswordRequest;
import com.assetshield.auth.web.dto.AuthDtos.ForgotPasswordResponse;
import com.assetshield.auth.web.dto.AuthDtos.LoginRequest;
import com.assetshield.auth.web.dto.AuthDtos.LogoutRequest;
import com.assetshield.auth.web.dto.AuthDtos.LogoutResponse;
import com.assetshield.auth.web.dto.AuthDtos.RefreshRequest;
import com.assetshield.auth.web.dto.AuthDtos.RefreshResponse;
import com.assetshield.auth.web.dto.AuthDtos.RegisterAgentRequest;
import com.assetshield.auth.web.dto.AuthDtos.RegisterRequest;
import com.assetshield.auth.web.dto.AuthDtos.RegisterResponse;
import com.assetshield.auth.web.dto.AuthDtos.ResendOtpRequest;
import com.assetshield.auth.web.dto.AuthDtos.ResendOtpResponse;
import com.assetshield.auth.web.dto.AuthDtos.ResetPasswordRequest;
import com.assetshield.auth.web.dto.AuthDtos.VerifyOtpRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, OTP verification, login, token refresh")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a property owner (sends OTP)")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(authService.register(request), "Registration started; OTP sent"));
    }

    @Operation(summary = "Register an insurance agent (sends OTP)")
    @PostMapping("/register-agent")
    public ResponseEntity<ApiResponse<RegisterResponse>> registerAgent(
            @Valid @RequestBody RegisterAgentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(authService.registerAgent(request), "Registration started; OTP sent"));
    }

    @Operation(summary = "Verify OTP, activate the account and issue tokens")
    @PostMapping("/verify-otp")
    public ApiResponse<AuthTokensResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ApiResponse.success(authService.verifyOtp(request), "Phone verified");
    }

    @Operation(summary = "Resend the registration OTP (throttled)")
    @PostMapping("/resend-otp")
    public ApiResponse<ResendOtpResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        return ApiResponse.success(authService.resendOtp(request), "OTP sent");
    }

    @Operation(summary = "Login with phone number and password")
    @PostMapping("/login")
    public ApiResponse<AuthTokensResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request), "Logged in");
    }

    @Operation(summary = "Request a password-reset code (no phone enumeration)")
    @PostMapping("/forgot-password")
    public ApiResponse<ForgotPasswordResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ApiResponse.success(authService.forgotPassword(request),
                "If this number has an account, a reset code has been sent");
    }

    @Operation(summary = "Reset the password with the SMS code; revokes all sessions")
    @PostMapping("/reset-password")
    public ApiResponse<LogoutResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.success(new LogoutResponse(true), "Password updated; please log in");
    }

    @Operation(summary = "Rotate a refresh token (reuse burns the family)")
    @PostMapping("/refresh")
    public ApiResponse<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success(authService.refresh(request), "Tokens refreshed");
    }

    @Operation(summary = "Logout: revoke the refresh token's entire family")
    @PostMapping("/logout")
    public ApiResponse<LogoutResponse> logout(Authentication authentication,
                                              @Valid @RequestBody LogoutRequest request) {
        authService.logout((UUID) authentication.getPrincipal(), request);
        return ApiResponse.success(new LogoutResponse(true), "Logged out");
    }
}
