package com.assetshield.property.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetshield.property.client.StubSubscriptionTierClient;
import com.assetshield.property.common.ApiException;
import com.assetshield.property.common.ErrorCode;
import com.assetshield.property.config.AppProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FreeTierGuardTest {

    private static final UUID USER = UUID.randomUUID();

    private static FreeTierGuard guard(String tier) {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("unused"), "unused", "unused", null,
                new AppProperties.Tier("stub", tier, "unused"),
                new AppProperties.Marketplace("log", "unused"),
                new AppProperties.Notifications("log"),
                new AppProperties.Events("log"), "unused",
                new AppProperties.Limits(1, 30));
        return new FreeTierGuard(new StubSubscriptionTierClient(tier), properties);
    }

    @Test
    void freeBlocksTheSecondProperty() {
        FreeTierGuard guard = guard("FREE");
        assertThatCode(() -> guard.checkPropertyQuota(USER, 0)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.checkPropertyQuota(USER, 1))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.FREE_TIER_LIMIT));
    }

    @Test
    void freeBlocksThe31stPhoto() {
        FreeTierGuard guard = guard("FREE");
        // 29 existing + 1 new = 30 → still within the cap
        assertThatCode(() -> guard.checkPhotoQuota(USER, 29, 1)).doesNotThrowAnyException();
        // 30 existing + 1 new = 31 → over the cap
        assertThatThrownBy(() -> guard.checkPhotoQuota(USER, 30, 1))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.FREE_TIER_LIMIT));
        // a multi-photo batch that would cross the cap is blocked too
        assertThatThrownBy(() -> guard.checkPhotoQuota(USER, 25, 10))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.FREE_TIER_LIMIT));
    }

    @Test
    void proIsNeverLimited() {
        FreeTierGuard guard = guard("PRO");
        assertThatCode(() -> guard.checkPropertyQuota(USER, 1)).doesNotThrowAnyException();
        assertThatCode(() -> guard.checkPropertyQuota(USER, 500)).doesNotThrowAnyException();
        assertThatCode(() -> guard.checkPhotoQuota(USER, 30, 1)).doesNotThrowAnyException();
        assertThatCode(() -> guard.checkPhotoQuota(USER, 10_000, 15)).doesNotThrowAnyException();
    }
}
