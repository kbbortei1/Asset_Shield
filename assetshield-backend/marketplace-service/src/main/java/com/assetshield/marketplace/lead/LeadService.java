package com.assetshield.marketplace.lead;

import com.assetshield.marketplace.agent.AgentGates;
import com.assetshield.marketplace.client.NotificationClient;
import com.assetshield.marketplace.client.PropertyClient;
import com.assetshield.marketplace.common.ApiException;
import com.assetshield.marketplace.common.ErrorCode;
import com.assetshield.marketplace.common.PageEnvelope;
import com.assetshield.marketplace.domain.AgentInterest;
import com.assetshield.marketplace.domain.InsuranceAgent;
import com.assetshield.marketplace.domain.InterestStatus;
import com.assetshield.marketplace.repo.AgentInterestRepository;
import com.assetshield.marketplace.security.AuthUser;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.ExpressInterestResponse;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.LeadDto;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeadService {

    private final AgentGates gates;
    private final PropertyClient propertyClient;
    private final AgentInterestRepository interestRepository;
    private final NotificationClient notificationClient;

    public LeadService(AgentGates gates, PropertyClient propertyClient,
                       AgentInterestRepository interestRepository,
                       NotificationClient notificationClient) {
        this.gates = gates;
        this.propertyClient = propertyClient;
        this.interestRepository = interestRepository;
        this.notificationClient = notificationClient;
    }

    /**
     * P0: items carry EXACTLY the five lead fields, built only from the
     * property internal lead projection — never from a property entity.
     */
    @Transactional(readOnly = true)
    public PageEnvelope<LeadDto> leads(AuthUser user, String propertyType, String locality,
                                       int page, int size) {
        gates.requireSubscribedAgent(user);
        PropertyClient.LeadPage leadPage = propertyClient.leads(propertyType, locality,
                PageEnvelope.clampPage(page), PageEnvelope.clampSize(size));
        return new PageEnvelope<>(
                leadPage.items().stream().map(item -> new LeadDto(item.propertyId(),
                        item.ownerDisplayName(), item.propertyName(), item.propertyType(),
                        item.locality())).toList(),
                leadPage.page(), leadPage.size(), leadPage.totalElements(), leadPage.totalPages());
    }

    @Transactional
    public ExpressInterestResponse expressInterest(AuthUser user, UUID propertyId) {
        InsuranceAgent agent = gates.requireSubscribedAgent(user);
        // 404 (never 403) for unknown, deleted and non-opted-in alike: the
        // existence of a property that is not on the marketplace is private.
        PropertyClient.PropertyInfo property = propertyClient.property(propertyId)
                .filter(info -> !info.deleted() && info.openToOffers())
                .orElseThrow(LeadService::leadNotFound);

        if (interestRepository.existsByAgentIdAndPropertyIdAndStatus(
                agent.getId(), propertyId, InterestStatus.PENDING)) {
            throw duplicatePending();
        }
        AgentInterest interest = new AgentInterest();
        interest.setAgentId(agent.getId());
        interest.setPropertyId(propertyId);
        interest.setOwnerUserId(property.ownerUserId());
        try {
            interest = interestRepository.saveAndFlush(interest);
        } catch (DataIntegrityViolationException e) {
            // race on ux_interest_pending — same answer as the pre-check
            throw duplicatePending();
        }

        notificationClient.send(property.ownerUserId(), "AGENT_INTEREST",
                "An insurance agent is interested in your property",
                "An agent from " + agent.getInsurerName() + " wants to connect about "
                        + property.name() + ".",
                Map.of("interestId", interest.getId().toString(),
                        "propertyId", propertyId.toString()));
        return new ExpressInterestResponse(interest.getId(), interest.getStatus().name());
    }

    private static ApiException leadNotFound() {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Property not found");
    }

    private static ApiException duplicatePending() {
        return new ApiException(ErrorCode.DUPLICATE_PENDING_INTEREST,
                "You already have a pending interest on this property");
    }
}
