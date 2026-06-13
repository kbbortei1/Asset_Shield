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
        assertThatCode(() -> guard.checkAssetQuota(USER, 29)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.checkAssetQuota(USER, 30))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.FREE_TIER_LIMIT));
    }

    @Test
    void proIsNeverLimited() {
        FreeTierGuard guard = guard("PRO");
        assertThatCode(() -> guard.checkPropertyQuota(USER, 1)).doesNotThrowAnyException();
        assertThatCode(() -> guard.checkPropertyQuota(USER, 500)).doesNotThrowAnyException();
        assertThatCode(() -> guard.checkAssetQuota(USER, 30)).doesNotThrowAnyException();
        assertThatCode(() -> guard.checkAssetQuota(USER, 10_000)).doesNotThrowAnyException();
    }
}
