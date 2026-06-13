package com.assetshield.marketplace.agent;

import com.assetshield.marketplace.client.AuthUserClient;
import com.assetshield.marketplace.client.NotificationClient;
import com.assetshield.marketplace.common.ApiException;
import com.assetshield.marketplace.common.ErrorCode;
import com.assetshield.marketplace.common.PageEnvelope;
import com.assetshield.marketplace.domain.InsuranceAgent;
import com.assetshield.marketplace.domain.VerificationStatus;
import com.assetshield.marketplace.repo.InsuranceAgentRepository;
import com.assetshield.marketplace.security.AuthUser;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.AdminAgentItem;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.VerifyAgentRequest;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.VerifyAgentResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentAdminService {

    private final InsuranceAgentRepository agentRepository;
    private final AuthUserClient authUserClient;
    private final NotificationClient notificationClient;

    public AgentAdminService(InsuranceAgentRepository agentRepository, AuthUserClient authUserClient,
                             NotificationClient notificationClient) {
        this.agentRepository = agentRepository;
        this.authUserClient = authUserClient;
        this.notificationClient = notificationClient;
    }

    @Transactional(readOnly = true)
    public PageEnvelope<AdminAgentItem> list(VerificationStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(PageEnvelope.clampPage(page), PageEnvelope.clampSize(size),
                Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<InsuranceAgent> agents = status == null
                ? agentRepository.findAll(pageable)
                : agentRepository.findByVerificationStatus(status, pageable);
        // name/phone via the 5-minute auth cache — one upstream call per distinct user
        return PageEnvelope.of(agents.map(agent -> {
            var user = authUserClient.byId(agent.getUserId());
            return new AdminAgentItem(agent.getId(), agent.getUserId(),
                    user.map(AuthUserClient.AuthUserInfo::fullName).orElse(null),
                    user.map(AuthUserClient.AuthUserInfo::phoneNumber).orElse(null),
                    agent.getInsurerName(), agent.getNicLicenceNo(),
                    agent.getVerificationStatus().name(), agent.getCreatedAt());
        }));
    }

    @Transactional
    public VerifyAgentResponse verify(AuthUser admin, UUID agentId, VerifyAgentRequest request) {
        InsuranceAgent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Agent not found"));
        if (agent.getVerificationStatus() != VerificationStatus.PENDING_VERIFICATION) {
            throw new ApiException(ErrorCode.ALREADY_DECIDED, "Agent verification already decided");
        }
        boolean approve = request.approve();
        if (!approve && (request.rejectionReason() == null || request.rejectionReason().isBlank())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "rejectionReason is required when rejecting",
                    Map.of("rejectionReason", "required when approve is false"));
        }
        agent.setVerificationStatus(approve ? VerificationStatus.VERIFIED : VerificationStatus.REJECTED);
        agent.setVerifiedByUserId(admin.id());
        agent.setVerifiedAt(Instant.now());
        agent.setRejectionReason(approve ? null : request.rejectionReason().trim());
        agentRepository.save(agent);

        notificationClient.send(agent.getUserId(), "AGENT_VERIFICATION",
                approve ? "Your agent account is verified" : "Your agent application was rejected",
                approve ? "You can now subscribe and browse marketplace leads."
                        : agent.getRejectionReason(),
                Map.of("agentId", agent.getId().toString(),
                        "status", agent.getVerificationStatus().name()));
        return new VerifyAgentResponse(agent.getId(), agent.getVerificationStatus().name(),
                agent.getVerifiedAt(), agent.getRejectionReason());
    }
}
