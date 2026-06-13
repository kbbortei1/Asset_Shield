package com.assetshield.auth.repo;

import com.assetshield.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // flushAutomatically (not clearAutomatically): clearing would discard
    // unflushed entities queued earlier in the same transaction.
    @Modifying(flushAutomatically = true)
    @Query("""
            update RefreshToken r set r.revokedAt = :now
            where r.familyId = :familyId and r.revokedAt is null
            """)
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update RefreshToken r set r.revokedAt = :now
            where r.userId = :userId and r.revokedAt is null
            """)
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
