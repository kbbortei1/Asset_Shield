package com.assetshield.auth.web;

import com.assetshield.auth.common.ApiResponse;
import com.assetshield.auth.service.UserService;
import com.assetshield.auth.web.dto.AuthDtos.GhanaCardResponse;
import com.assetshield.auth.web.dto.AuthDtos.ProfileResponse;
import com.assetshield.auth.web.dto.AuthDtos.PurgeResponse;
import com.assetshield.auth.web.dto.AuthDtos.UpdateProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Profile", description = "Authenticated user profile management")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Get the authenticated user's profile")
    @GetMapping("/me")
    public ApiResponse<ProfileResponse> me(Authentication authentication) {
        return ApiResponse.success(userService.me(principal(authentication)), "Profile fetched");
    }

    @Operation(summary = "Update full name and/or language")
    @PutMapping("/me")
    public ApiResponse<ProfileResponse> updateMe(Authentication authentication,
                                                 @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(userService.updateMe(principal(authentication), request), "Profile updated");
    }

    @Operation(summary = "Upload the Ghana Card image (jpeg/png, max 10 MB)")
    @PostMapping(value = "/me/ghana-card", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<GhanaCardResponse> uploadGhanaCard(Authentication authentication,
                                                          @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(userService.uploadGhanaCard(principal(authentication), file),
                "Ghana Card uploaded");
    }

    @Operation(summary = "Request account purge (30-day grace, revokes all sessions)")
    @DeleteMapping("/me")
    public ApiResponse<PurgeResponse> deleteMe(Authentication authentication) {
        return ApiResponse.success(userService.requestPurge(principal(authentication)), "Purge scheduled");
    }

    private static UUID principal(Authentication authentication) {
        return (UUID) authentication.getPrincipal();
    }
}
