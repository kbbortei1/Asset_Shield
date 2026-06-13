package com.assetshield.marketplace.lead;

import com.assetshield.marketplace.agent.AgentGates;
import com.assetshield.marketplace.client.AuthUserClient;
import com.assetshield.marketplace.client.NotificationClient;
import com.assetshield.marketplace.client.PropertyClient;
import com.assetshield.marketplace.common.ApiException;
import com.assetshield.marketplace.common.ErrorCode;
import com.assetshield.marketplace.common.PageEnvelope;
import com.assetshield.marketplace.domain.AgentInterest;
import com.assetshield.marketplace.domain.InsuranceAgent;
import com.assetshield.marketplace.domain.InterestStatus;
import com.assetshield.marketplace.repo.AgentInterestRepository;
import com.assetshield.marketplace.repo.DossierShareRepository;
import com.assetshield.marketplace.repo.InsuranceAgentRepository;
import com.assetshield.marketplace.security.AuthUser;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.AgentInterestItem;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.InterestRespondResponse;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.OwnerInterestItem;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.RevokeInterestResponse;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterestService {

    private final AgentGates gates;
    private final AgentInterestRepository interestRepository;
    private final InsuranceAgentRepository agentRepository;
    private final DossierShareRepository shareRepository;
    private final AuthUserClient authUserClient;
    private final PropertyClient propertyClient;
    private final NotificationClient notificationClient;

    public InterestService(AgentGates gates, AgentInterestRepository interestRepository,
                           InsuranceAgentRepository agentRepository,
                           DossierShareRepository shareRepository, AuthUserClient authUserClient,
                           PropertyClient propertyClient, NotificationClient notificationClient) {
        this.gates = gates;
        this.interestRepository = interestRepository;
        this.agentRepository = agentRepository;
        this.shareRepository = shareRepository;
        this.authUserClient = authUserClient;
        this.propertyClient = propertyClient;
        this.notificationClient = notificationClient;
    }

    // ── agent side ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageEnvelope<AgentInterestItem> agentInterests(AuthUser user, int page, int size) {
        InsuranceAgent agent = gates.requireSubscribedAgent(user);
        Pageable pageable = PageRequest.of(PageEnvelope.clampPage(page), PageEnvelope.clampSize(size));
        return PageEnvelope.of(interestRepository
                .findByAgentIdOrderByCreatedAtDesc(agent.getId(), pageable)
                .map(this::toAgentItem));
    }

    private AgentInterestItem toAgentItem(AgentInterest interest) {
        Optional<PropertyClient.PropertyInfo> property =
                propertyClient.propertyCached(interest.getPropertyId());
        // before acceptance the agent sees nothing beyond the lead projection
        String ownerFullName = interest.getStatus() == InterestStatus.ACCEPTED
                ? authUserClient.byId(interest.getOwnerUserId())
                        .map(AuthUserClient.AuthUserInfo::fullName).orElse(null)
                : null;
        return new AgentInterestItem(interest.getId(),
                property.map(PropertyClient.PropertyInfo::name).orElse(null),
                property.map(PropertyClient.PropertyInfo::locality).orElse(null),
                interest.getStatus().name(), interest.getCreatedAt(), interest.getRespondedAt(),
                ownerFullName);
    }

    // ── owner side ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageEnvelope<OwnerInterestItem> ownerInterests(AuthUser user, InterestStatus status,
                                                          int page, int size) {
        Pageable pageable = PageRequest.of(PageEnvelope.clampPage(page), PageEnvelope.clampSize(size));
        Page<AgentInterest> interests = status == null
                ? interestRepository.findByOwnerUserIdOrderByCreatedAtDesc(user.id(), pageable)
                : interestRepository.findByOwnerUserIdAndStatusOrderByCreatedAtDesc(
                        user.id(), status, pageable);
        return PageEnvelope.of(interests.map(this::toOwnerItem));
    }

    private OwnerInterestItem toOwnerItem(AgentInterest interest) {
        InsuranceAgent agent = agentRepository.findById(interest.getAgentId()).orElse(null);
        String agentName = agent == null ? null
                : authUserClient.byId(agent.getUserId())
                        .map(AuthUserClient.AuthUserInfo::fullName).orElse(null);
        // licence number is due diligence data — owners get it once connected
        String licence = agent != null && interest.getStatus() == InterestStatus.ACCEPTED
                ? agent.getNicLicenceNo() : null;
        return new OwnerInterestItem(interest.getId(), agentName,
                agent == null ? null : agent.getInsurerName(),
                propertyClient.propertyCached(interest.getPropertyId())
                        .map(PropertyClient.PropertyInfo::name).orElse(null),
                interest.getStatus().name(), interest.getCreatedAt(), licence);
    }

    @Transactional
    public InterestRespondResponse respond(AuthUser user, UUID interestId, boolean accept) {
        AgentInterest interest = requireOwnInterest(user, interestId);
        if (interest.getStatus() != InterestStatus.PENDING) {
            throw new ApiException(ErrorCode.ALREADY_RESPONDED, "Interest already responded to");
        }
        interest.setStatus(accept ? InterestStatus.ACCEPTED : InterestStatus.DECLINED);
        interest.setRespondedAt(Instant.now());
        interestRepository.save(interest);

        notifyAgent(interest, "INTEREST_RESPONSE",
                accept ? "Your interest was accepted" : "Your interest was declined",
                accept ? "The owner accepted your connection request."
                        : "The owner declined your connection request.");
        return new InterestRespondResponse(interest.getId(), interest.getStatus().name(),
                interest.getRespondedAt());
    }

    /**
     * FR28: consent is revocable. Revoking the connection also revokes every
     * dossier share under it in the same transaction; the agent's next read
     * of anything behind this consent is a 404.
     */
    @Transactional
    public RevokeInterestResponse revoke(AuthUser user, UUID interestId) {
        AgentInterest interest = requireOwnInterest(user, interestId);
        if (interest.getStatus() != InterestStatus.ACCEPTED) {
            throw new ApiException(ErrorCode.ALREADY_RESPONDED,
                    "Only accepted connections can be revoked");
        }
        interest.setStatus(InterestStatus.REVOKED);
        interestRepository.save(interest);
        long revokedShares = shareRepository.revokeByInterest(interest.getId(), Instant.now());

        notifyAgent(interest, "INTEREST_REVOKED", "A connection was revoked",
                "The owner revoked your connection and any shared dossiers.");
        return new RevokeInterestResponse(interest.getId(), interest.getStatus().name(), revokedShares);
    }

    /** Owner mismatch is a 404, not a 403 — interests must not be enumerable. */
    private AgentInterest requireOwnInterest(AuthUser user, UUID interestId) {
        return interestRepository.findById(interestId)
                .filter(interest -> interest.getOwnerUserId().equals(user.id()))
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Interest not found"));
    }

    private void notifyAgent(AgentInterest interest, String type, String title, String body) {
        agentRepository.findById(interest.getAgentId()).ifPresent(agent ->
                notificationClient.send(agent.getUserId(), type, title, body,
                        Map.of("interestId", interest.getId().toString(),
                                "status", interest.getStatus().name())));
    }
}
