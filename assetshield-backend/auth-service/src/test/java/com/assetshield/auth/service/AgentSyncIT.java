package com.assetshield.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assetshield.auth.TestProps;
import com.assetshield.auth.client.MarketplaceAgentSyncClient;
import com.assetshield.auth.domain.PendingAgentDetails;
import com.assetshield.auth.repo.PendingAgentDetailsRepository;
import com.assetshield.auth.repo.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** auth → marketplace agent sync: OTP push, re-push job, licence conflicts. */
@SpringBootTest(properties = {
        "JWT_SECRET=" + TestProps.JWT_SECRET,
        "INTERNAL_API_KEY=" + TestProps.INTERNAL_API_KEY,
        "OTP_DEV_CODE=" + TestProps.DEV_CODE,
        "SMS_PROVIDER=mock",
        "STORAGE_PROVIDER=local",
        "STORAGE_LOCAL_ROOT=target/it-storage-agent-sync",
        // tests drive repushUnconsumed() directly — keep the scheduler out
        "app.agent-sync.initial-delay-ms=86400000"
})
@AutoConfigureMockMvc
@Testcontainers
class AgentSyncIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AgentSyncService agentSyncService;

    @Autowired
    PendingAgentDetailsRepository pendingAgentRepository;

    @Autowired
    UserRepository userRepository;

    @MockitoBean
    MarketplaceAgentSyncClient syncClient;

    private String registerAgent(String phone, String licence) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register-agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"%s","password":"secret123","fullName":"Kojo Agent",
                                 "insurerName":"Star Assurance","nicLicenceNo":"%s"}
                                """.formatted(phone, licence)))
                .andExpect(status().isCreated());
        return phone;
    }

    private void verifyOtp(String phone) throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"%s","code":"%s"}
                                """.formatted(phone, TestProps.DEV_CODE)))
                .andExpect(status().isOk());
    }

    private PendingAgentDetails detailsFor(String phone) {
        UUID userId = userRepository.findByPhoneNumberAndDeletedAtIsNull(phone).orElseThrow().getId();
        return pendingAgentRepository.findByUserId(userId).orElseThrow();
    }

    @Test
    void otpCompletionPushesTheSyncAndMarksConsumed() throws Exception {
        when(syncClient.sync(any(), anyString(), anyString()))
                .thenReturn(MarketplaceAgentSyncClient.SyncResult.SYNCED);
        String phone = registerAgent("+233244600001", "NIC-SYNC-1");
        assertThat(detailsFor(phone).getConsumedAt()).isNull();

        verifyOtp(phone);

        PendingAgentDetails details = detailsFor(phone);
        assertThat(details.getConsumedAt()).isNotNull();
        verify(syncClient).sync(eq(details.getUserId()), eq("Star Assurance"), eq("NIC-SYNC-1"));
    }

    @Test
    void marketplaceDowntimeLeavesTheRowForTheRepushJob() throws Exception {
        when(syncClient.sync(any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("marketplace down"));
        String phone = registerAgent("+233244600002", "NIC-SYNC-2");
        verifyOtp(phone); // best-effort: the OTP flow itself must still succeed

        assertThat(detailsFor(phone).getConsumedAt()).isNull();

        // marketplace recovers → the 60 s job re-pushes and consumes
        org.mockito.Mockito.reset(syncClient);
        when(syncClient.sync(any(), anyString(), anyString()))
                .thenReturn(MarketplaceAgentSyncClient.SyncResult.SYNCED);
        agentSyncService.repushUnconsumed();
        assertThat(detailsFor(phone).getConsumedAt()).isNotNull();
    }

    @Test
    void licenceConflictIsConsumedAndNeverRetried() throws Exception {
        when(syncClient.sync(any(), anyString(), anyString()))
                .thenReturn(MarketplaceAgentSyncClient.SyncResult.LICENCE_CONFLICT);
        String phone = registerAgent("+233244600003", "NIC-SYNC-3");
        verifyOtp(phone);

        assertThat(detailsFor(phone).getConsumedAt()).isNotNull();
        verify(syncClient, times(1)).sync(any(), anyString(), anyString());

        // consumed rows are out of the re-push set for good
        agentSyncService.repushUnconsumed();
        verifyNoMoreInteractions(syncClient);
    }

    @Test
    void unverifiedAgentsAreNotPushedByTheJob() throws Exception {
        when(syncClient.sync(any(), anyString(), anyString()))
                .thenReturn(MarketplaceAgentSyncClient.SyncResult.SYNCED);
        registerAgent("+233244600004", "NIC-SYNC-4"); // never completes OTP

        agentSyncService.repushUnconsumed();
        verify(syncClient, times(0)).sync(any(), anyString(), anyString());
    }
}
