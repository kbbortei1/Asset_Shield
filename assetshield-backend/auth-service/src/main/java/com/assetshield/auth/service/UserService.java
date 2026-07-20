package com.assetshield.auth.service;

import com.assetshield.auth.common.ApiException;
import com.assetshield.auth.common.ErrorCode;
import com.assetshield.auth.domain.Role;
import com.assetshield.auth.domain.User;
import com.assetshield.auth.domain.UserStatus;
import com.assetshield.auth.repo.UserRepository;
import com.assetshield.auth.storage.StorageProvider;
import com.assetshield.auth.token.RefreshTokenService;
import com.assetshield.auth.web.dto.AuthDtos.CreateAdminRequest;
import com.assetshield.auth.web.dto.AuthDtos.CreateAdminResponse;
import com.assetshield.auth.web.dto.AuthDtos.GhanaCardResponse;
import com.assetshield.auth.web.dto.AuthDtos.InternalUserResponse;
import com.assetshield.auth.web.dto.AuthDtos.ProfileResponse;
import com.assetshield.auth.web.dto.AuthDtos.PurgeResponse;
import com.assetshield.auth.web.dto.AuthDtos.UpdateProfileRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserService {

    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of(MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE);

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final StorageProvider storageProvider;
    private final AuditService auditService;

    public UserService(UserRepository userRepository, RefreshTokenService refreshTokenService,
                       PasswordEncoder passwordEncoder, StorageProvider storageProvider,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.storageProvider = storageProvider;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public ProfileResponse me(UUID userId) {
        return toProfile(requireUser(userId));
    }

    @Transactional
    public ProfileResponse updateMe(UUID userId, UpdateProfileRequest request) {
        User user = requireUser(userId);
        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.language() != null && !request.language().isBlank()) {
            user.setLanguage(request.language());
        }
        return toProfile(userRepository.save(user));
    }

    /**
     * Deactivates the account immediately: soft-deletes the row (all lookups
     * filter deleted_at IS NULL, and the partial unique index ux_users_phone
     * frees the phone number for re-registration), scrubs PII, deletes the
     * stored Ghana Card image and revokes every session. The row itself is kept
     * for referential/audit integrity. Data held by other services (properties,
     * reports) is orphaned by userId and needs a cross-service purge later.
     */
    @Transactional
    public PurgeResponse requestPurge(UUID userId) {
        User user = requireUser(userId);
        Instant now = Instant.now();
        if (user.getGhanaCardUrl() != null) {
            try {
                storageProvider.delete(user.getGhanaCardUrl());
            } catch (RuntimeException e) {
                // best-effort: an orphaned image must not block account deletion
            }
            user.setGhanaCardUrl(null);
        }
        user.setPurgeRequestedAt(now);
        user.setDeletedAt(now);
        user.setStatus(UserStatus.SUSPENDED);
        user.setFullName("Deleted account");
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(userId);
        auditService.record(userId, AuditService.ACCOUNT_PURGED, null,
                "User requested account deletion; PII scrubbed, sessions revoked");
        return new PurgeResponse(true, now);
    }

    /** Stores the Ghana Card image at ghana-cards/{userId} and flags the profile. */
    @Transactional
    public GhanaCardResponse uploadGhanaCard(UUID userId, MultipartFile file) {
        User user = requireUser(userId);
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Uploaded file is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Only image/jpeg and image/png uploads are accepted");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read uploaded file", e);
        }
        String extension = MediaType.IMAGE_PNG_VALUE.equalsIgnoreCase(contentType) ? ".png" : ".jpg";
        String objectPath = storageProvider.store(bytes, "ghana-cards/" + userId + extension, contentType);
        user.setGhanaCardUrl(objectPath);
        userRepository.save(user);
        return new GhanaCardResponse(true);
    }

    @Transactional
    public CreateAdminResponse createAdmin(UUID actorUserId, CreateAdminRequest request) {
        userRepository.findByPhoneNumberAndDeletedAtIsNull(request.phoneNumber())
                .ifPresent(existing -> {
                    throw new ApiException(ErrorCode.PHONE_EXISTS, "Phone number is already registered");
                });
        User admin = new User();
        admin.setPhoneNumber(request.phoneNumber());
        admin.setPasswordHash(passwordEncoder.encode(request.password()));
        admin.setFullName(request.fullName().trim());
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        UUID adminId = userRepository.save(admin).getId();
        auditService.record(actorUserId, AuditService.ADMIN_CREATED, request.phoneNumber(),
                "New admin account created");
        return new CreateAdminResponse(adminId);
    }

    @Transactional(readOnly = true)
    public InternalUserResponse internalById(UUID id) {
        return toInternal(requireUser(id));
    }

    @Transactional(readOnly = true)
    public InternalUserResponse internalByPhone(String phone) {
        return userRepository.findByPhoneNumberAndDeletedAtIsNull(phone)
                .map(UserService::toInternal)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
    }

    private User requireUser(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
    }

    private static ProfileResponse toProfile(User user) {
        return new ProfileResponse(user.getId(), user.getPhoneNumber(), user.getFullName(),
                user.getRole().name(), user.getLanguage(),
                user.getGhanaCardUrl() != null, user.getCreatedAt());
    }

    private static InternalUserResponse toInternal(User user) {
        return new InternalUserResponse(user.getId(), user.getFullName(), user.getPhoneNumber(),
                user.getRole().name(), user.getStatus().name());
    }
}
