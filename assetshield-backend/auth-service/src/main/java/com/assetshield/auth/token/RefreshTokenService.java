package com.assetshield.auth.token;

import com.assetshield.auth.common.ApiException;
import com.assetshield.auth.common.ErrorCode;
import com.assetshield.auth.config.AppProperties;
import com.assetshield.auth.domain.RefreshToken;
import com.assetshield.auth.repo.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque refresh tokens (256-bit random, SHA-256 stored) with rotation and
 * reuse detection. Reusing an already-rotated token burns its whole family.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final long ttlDays;

    public RefreshTokenService(RefreshTokenRepository repository, AppProperties properties) {
        this.repository = repository;
        this.ttlDays = properties.refresh().ttlDays();
    }

    public record IssuedToken(String raw, RefreshToken entity) {
    }

    public record RotatedToken(String raw, UUID userId) {
    }

    @Transactional
    public IssuedToken issue(UUID userId, UUID familyId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(sha256Hex(raw));
        token.setFamilyId(familyId);
        token.setExpiresAt(Instant.now().plus(Duration.ofDays(ttlDays)));
        return new IssuedToken(raw, repository.save(token));
    }

    /**
     * Rotates the presented token. The reuse-detection family revocation must
     * survive the thrown 401, hence noRollbackFor.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public RotatedToken rotate(String raw) {
        RefreshToken presented = repository.findByTokenHash(sha256Hex(raw))
                .orElseThrow(() -> new ApiException(ErrorCode.REFRESH_INVALID, "Refresh token is invalid"));

        if (presented.getRevokedAt() != null) {
            int burned = repository.revokeFamily(presented.getFamilyId(), Instant.now());
            log.warn("Refresh token reuse detected for user {}; revoked {} tokens in family {}",
                    presented.getUserId(), burned, presented.getFamilyId());
            throw new ApiException(ErrorCode.REFRESH_REUSED, "Refresh token was already used");
        }
        if (presented.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.REFRESH_EXPIRED, "Refresh token has expired");
        }

        IssuedToken successor = issue(presented.getUserId(), presented.getFamilyId());
        presented.setRevokedAt(Instant.now());
        presented.setReplacedBy(successor.entity().getId());
        repository.save(presented);
        return new RotatedToken(successor.raw(), presented.getUserId());
    }

    /** Logout: revoke the presented token's entire family (must belong to the caller). */
    @Transactional
    public void revokeFamilyOf(String raw, UUID callerId) {
        RefreshToken presented = repository.findByTokenHash(sha256Hex(raw))
                .orElseThrow(() -> new ApiException(ErrorCode.REFRESH_INVALID, "Refresh token is invalid"));
        if (!presented.getUserId().equals(callerId)) {
            throw new ApiException(ErrorCode.REFRESH_INVALID, "Refresh token is invalid");
        }
        repository.revokeFamily(presented.getFamilyId(), Instant.now());
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        repository.revokeAllForUser(userId, Instant.now());
    }

    static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
