package com.assetshield.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetshield.auth.TestProps;
import com.assetshield.auth.common.ApiException;
import com.assetshield.auth.common.ErrorCode;
import com.assetshield.auth.domain.RefreshToken;
import com.assetshield.auth.repo.RefreshTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    RefreshTokenRepository repository;

    RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repository, TestProps.appProperties(3600));
    }

    private RefreshToken stored(UUID userId, UUID familyId, String raw) {
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setFamilyId(familyId);
        token.setTokenHash(RefreshTokenService.sha256Hex(raw));
        token.setExpiresAt(Instant.now().plus(Duration.ofDays(14)));
        return token;
    }

    @Test
    void issueStoresSha256HashNotTheRawToken() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.IssuedToken issued = service.issue(UUID.randomUUID(), UUID.randomUUID());

        assertThat(issued.raw()).isNotBlank();
        assertThat(issued.entity().getTokenHash())
                .isEqualTo(RefreshTokenService.sha256Hex(issued.raw()))
                .hasSize(64)
                .isNotEqualTo(issued.raw());
    }

    @Test
    void rotateRevokesPresentedTokenAndIssuesSuccessorInSameFamily() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        RefreshToken presented = stored(userId, familyId, "old-raw-token");
        when(repository.findByTokenHash(RefreshTokenService.sha256Hex("old-raw-token")))
                .thenReturn(Optional.of(presented));

        RefreshTokenService.RotatedToken rotated = service.rotate("old-raw-token");

        assertThat(rotated.userId()).isEqualTo(userId);
        assertThat(rotated.raw()).isNotEqualTo("old-raw-token");
        assertThat(presented.getRevokedAt()).isNotNull();
        verify(repository).save(presented);
        // Successor keeps the family for future reuse detection.
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(token ->
                token != presented && familyId.equals(token.getFamilyId()) && userId.equals(token.getUserId())));
    }

    @Test
    void reusingARevokedTokenBurnsTheWholeFamily() {
        UUID familyId = UUID.randomUUID();
        RefreshToken reused = stored(UUID.randomUUID(), familyId, "reused-raw");
        reused.setRevokedAt(Instant.now().minusSeconds(5));
        when(repository.findByTokenHash(RefreshTokenService.sha256Hex("reused-raw")))
                .thenReturn(Optional.of(reused));

        assertThatThrownBy(() -> service.rotate("reused-raw"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.REFRESH_REUSED));

        verify(repository).revokeFamily(eq(familyId), any(Instant.class));
        verify(repository, never()).save(any());
    }

    @Test
    void expiredTokenIsRejectedWithoutBurningTheFamily() {
        RefreshToken expired = stored(UUID.randomUUID(), UUID.randomUUID(), "expired-raw");
        expired.setExpiresAt(Instant.now().minusSeconds(5));
        when(repository.findByTokenHash(RefreshTokenService.sha256Hex("expired-raw")))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("expired-raw"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.REFRESH_EXPIRED));

        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    void unknownTokenIsRejectedAsInvalid() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("never-issued"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.REFRESH_INVALID));
    }

    @Test
    void logoutRejectsTokensBelongingToAnotherUser() {
        RefreshToken token = stored(UUID.randomUUID(), UUID.randomUUID(), "raw");
        when(repository.findByTokenHash(RefreshTokenService.sha256Hex("raw")))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.revokeFamilyOf("raw", UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.REFRESH_INVALID));
        verify(repository, never()).revokeFamily(any(), any());
    }
}
