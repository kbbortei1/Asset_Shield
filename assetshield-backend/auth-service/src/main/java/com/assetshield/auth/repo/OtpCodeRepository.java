package com.assetshield.auth.repo;

import com.assetshield.auth.domain.OtpCode;
import com.assetshield.auth.domain.OtpPurpose;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {

    Optional<OtpCode> findTopByPhoneNumberAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String phoneNumber, OtpPurpose purpose);

    // flushAutomatically (not clearAutomatically): clearing would discard
    // unflushed entities queued earlier in the same transaction.
    @Modifying(flushAutomatically = true)
    @Query("""
            update OtpCode o set o.consumedAt = :now
            where o.phoneNumber = :phone and o.purpose = :purpose and o.consumedAt is null
            """)
    int consumeAllActive(@Param("phone") String phone, @Param("purpose") OtpPurpose purpose,
                         @Param("now") Instant now);
}
