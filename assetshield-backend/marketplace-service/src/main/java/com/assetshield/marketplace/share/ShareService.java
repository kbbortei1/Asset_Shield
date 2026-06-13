package com.assetshield.marketplace.share;

import com.assetshield.marketplace.agent.AgentGates;
import com.assetshield.marketplace.client.AuthUserClient;
import com.assetshield.marketplace.client.DossierClient;
import com.assetshield.marketplace.client.NotificationClient;
import com.assetshield.marketplace.client.PropertyClient;
import com.assetshield.marketplace.common.ApiException;
import com.assetshield.marketplace.common.ErrorCode;
import com.assetshield.marketplace.common.PageEnvelope;
import com.assetshield.marketplace.domain.AgentInterest;
import com.assetshield.marketplace.domain.DossierShare;
import com.assetshield.marketplace.domain.InsuranceAgent;
import com.assetshield.marketplace.domain.InterestStatus;
import com.assetshield.marketplace.repo.AgentInterestRepository;
import com.assetshield.marketplace.repo.DossierShareRepository;
import com.assetshield.marketplace.repo.InsuranceAgentRepository;
import com.assetshield.marketplace.security.AuthUser;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.RevokeShareResponse;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.ShareResponse;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.SharedDossierItem;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShareService {

    private final AgentGates gates;
    private final DossierShareRepository shareRepository;
    private final AgentInterestRepository interestRepository;
    private final InsuranceAgentRepository agentRepository;
    private final DossierClient dossierClient;
    private final PropertyClient propertyClient;
    private final AuthUserClient authUserClient;
    private final NotificationClient notificationClient;

    public ShareService(AgentGates gates, DossierShareRepository shareRepository,
                        AgentInterestRepository interestRepository,
                        InsuranceAgentRepository agentRepository, DossierClient dossierClient,
                        PropertyClient propertyClient, AuthUserClient authUserClient,
                        NotificationClient notificationClient) {
        this.gates = gates;
        this.shareRepository = shareRepository;
        this.interestRepository = interestRepository;
        this.agentRepository = agentRepository;
        this.dossierClient = dossierClient;
        this.propertyClient = propertyClient;
        this.authUserClient = authUserClient;
        this.notificationClient = notificationClient;
    }

    // ── owner side ───────────────────────────────────────────────────────────

    @Transactional
    public ShareResponse share(AuthUser user, UUID dossierId, UUID agentInterestId) {
        DossierClient.DossierMeta meta = dossierClient.meta(dossierId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Dossier not found"));
        if (!"READY".equals(meta.status())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Only READY dossiers can be shared");
        }
        requireExportAccess(user, meta.propertyId());

        AgentInterest interest = interestRepository.findById(agentInterestId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Interest not found"));
        if (interest.getStatus() != InterestStatus.ACCEPTED) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Dossiers can only be shared on an accepted connection");
        }
        if (!interest.getPropertyId().equals(meta.propertyId())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Interest does not belong to this dossier's property");
        }
        if (shareRepository.existsByDossierIdAndAgentIdAndRevokedAtIsNull(
                dossierId, interest.getAgentId())) {
            throw alreadyShared();
        }
        DossierShare share = new DossierShare();
        share.setDossierId(dossierId);
        share.setAgentId(interest.getAgentId());
        share.setAgentInterestId(interest.getId());
        share.setSharedByUserId(user.id());
        share.setConsentAt(Instant.now());
        try {
            share = shareRepository.saveAndFlush(share);
        } catch (DataIntegrityViolationException e) {
            // race on ux_share_active — same answer as the pre-check
            throw alreadyShared();
        }

        DossierShare saved = share;
        agentRepository.findById(interest.getAgentId()).ifPresent(agent ->
                notificationClient.send(agent.getUserId(), "DOSSIER_SHARED",
                        "A dossier was shared with you",
                        "The owner shared a damage dossier with you for review.",
                        Map.of("dossierId", dossierId.toString(),
                                "shareId", saved.getId().toString())));
        return new ShareResponse(share.getId(), share.getConsentAt());
    }

    @Transactional
    public RevokeShareResponse revokeShare(AuthUser user, UUID dossierId, UUID agentId) {
        DossierClient.DossierMeta meta = dossierClient.meta(dossierId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Dossier not found"));
        requireExportAccess(user, meta.propertyId());
        DossierShare share = shareRepository
                .findByDossierIdAndAgentIdAndRevokedAtIsNull(dossierId, agentId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Share not found"));
        share.setRevokedAt(Instant.now());
        shareRepository.save(share);

        agentRepository.findById(agentId).ifPresent(agent ->
                notificationClient.send(agent.getUserId(), "SHARE_REVOKED",
                        "A shared dossier was revoked",
                        "The owner revoked your access to a shared dossier.",
                        Map.of("dossierId", dossierId.toString())));
        return new RevokeShareResponse(share.getId(), share.getRevokedAt());
    }

    /** Sharing consent requires OWNER or MEMBER_EXPORT on the dossier's property. */
    private void requireExportAccess(AuthUser user, UUID propertyId) {
        String access = propertyClient.access(propertyId, user.id());
        if (!"OWNER".equals(access) && !"MEMBER_EXPORT".equals(access)) {
            throw new ApiException(ErrorCode.NOT_OWNER,
                    "Only the owner or an export-enabled member can manage dossier shares");
        }
    }

    private static ApiException alreadyShared() {
        return new ApiException(ErrorCode.ALREADY_SHARED,
                "This dossier is already shared with that agent");
    }

    // ── agent side ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageEnvelope<SharedDossierItem> sharedDossiers(AuthUser user, int page, int size) {
        InsuranceAgent agent = gates.requireSubscribedAgent(user);
        return PageEnvelope.of(shareRepository
                .findActiveByAgent(agent.getId(),
                        PageRequest.of(PageEnvelope.clampPage(page), PageEnvelope.clampSize(size)))
                .map(this::toSharedItem));
    }

    private SharedDossierItem toSharedItem(DossierShare share) {
        Optional<DossierClient.DossierMeta> meta = dossierClient.metaCached(share.getDossierId());
        Optional<AgentInterest> interest = interestRepository.findById(share.getAgentInterestId());
        String ownerName = interest.flatMap(i -> authUserClient.byId(i.getOwnerUserId()))
                .map(AuthUserClient.AuthUserInfo::fullName).orElse(null);
        String propertyName = interest.flatMap(i -> propertyClient.propertyCached(i.getPropertyId()))
                .map(PropertyClient.PropertyInfo::name).orElse(null);
        return new SharedDossierItem(share.getDossierId(), share.getId(), ownerName, propertyName,
                meta.map(DossierClient.DossierMeta::disasterType).orElse(null),
                meta.map(DossierClient.DossierMeta::totalEstimatedLoss).orElse(null),
                share.getConsentAt());
    }

    /**
     * The consent re-check every agent dossier read goes through: an
     * unrevoked share whose interest is still ACCEPTED — anything less is a
     * 404 (the dossier's existence is itself private). Revocation is
     * enforced here, on the next read, not just at share time.
     */
    @Transactional(readOnly = true)
    public DossierShare requireActiveShare(AuthUser user, UUID dossierId) {
        InsuranceAgent agent = gates.requireSubscribedAgent(user);
        return shareRepository.findByDossierIdAndAgentIdAndRevokedAtIsNull(dossierId, agent.getId())
                .filter(share -> interestRepository.findById(share.getAgentInterestId())
                        .map(interest -> interest.getStatus() == InterestStatus.ACCEPTED)
                        .orElse(false))
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Dossier not found"));
    }
}
