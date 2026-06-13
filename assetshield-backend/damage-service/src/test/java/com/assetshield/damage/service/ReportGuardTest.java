package com.assetshield.damage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.assetshield.damage.client.AccessLevel;
import com.assetshield.damage.client.PropertyInternalClient;
import com.assetshield.damage.common.ApiException;
import com.assetshield.damage.common.ErrorCode;
import com.assetshield.damage.domain.DamageReport;
import com.assetshield.damage.domain.ReportStatus;
import com.assetshield.damage.repo.DamageReportRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportGuardTest {

    private final PropertyInternalClient propertyClient = mock(PropertyInternalClient.class);
    private final DamageReportRepository reportRepository = mock(DamageReportRepository.class);
    private final ReportGuard guard = new ReportGuard(propertyClient, reportRepository);

    private final UUID propertyId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private void accessIs(AccessLevel level) {
        when(propertyClient.access(any(), any())).thenReturn(level);
    }

    @Test
    void ownerAndExportMemberMayMutate() {
        accessIs(AccessLevel.OWNER);
        assertThatCode(() -> guard.requireMutate(propertyId, userId)).doesNotThrowAnyException();
        accessIs(AccessLevel.MEMBER_EXPORT);
        assertThatCode(() -> guard.requireMutate(propertyId, userId)).doesNotThrowAnyException();
    }

    @Test
    void plainMemberMayViewButNotMutate() {
        accessIs(AccessLevel.MEMBER);
        assertThatCode(() -> guard.requireView(propertyId, userId)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.requireMutate(propertyId, userId))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.NOT_OWNER));
    }

    @Test
    void outsiderIsRejectedEverywhere() {
        accessIs(AccessLevel.NONE);
        assertThatThrownBy(() -> guard.requireView(propertyId, userId))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.NOT_MEMBER));
        assertThatThrownBy(() -> guard.requireMutate(propertyId, userId))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.NOT_MEMBER));
    }

    @Test
    void completedReportRejectsEveryMutation() {
        DamageReport completed = new DamageReport();
        completed.setStatus(ReportStatus.COMPLETED);
        assertThatThrownBy(() -> guard.requireDraft(completed))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));

        DamageReport draft = new DamageReport();
        draft.setStatus(ReportStatus.DRAFT);
        assertThatCode(() -> guard.requireDraft(draft)).doesNotThrowAnyException();
    }
}
