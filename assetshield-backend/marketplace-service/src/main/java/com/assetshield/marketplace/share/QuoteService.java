package com.assetshield.marketplace.share;

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
import com.assetshield.marketplace.domain.PolicyQuote;
import com.assetshield.marketplace.domain.QuoteStatus;
import com.assetshield.marketplace.repo.AgentInterestRepository;
import com.assetshield.marketplace.repo.InsuranceAgentRepository;
import com.assetshield.marketplace.repo.PolicyQuoteRepository;
import com.assetshield.marketplace.security.AuthUser;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.DossierVerifyView;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.MismatchView;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.OwnerQuoteItem;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.QuoteCreateRequest;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.QuoteCreateResponse;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.QuoteRespondResponse;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);

    private final ShareService shareService;
    private final PolicyQuoteRepository quoteRepository;
    private final AgentInterestRepository interestRepository;
    private final InsuranceAgentRepository agentRepository;
    private final DossierClient dossierClient;
    private final PropertyClient propertyClient;
    private final AuthUserClient authUserClient;
    private final NotificationClient notificationClient;

    public QuoteService(ShareService shareService, PolicyQuoteRepository quoteRepository,
                        AgentInterestRepository interestRepository,
                        InsuranceAgentRepository agentRepository, DossierClient dossierClient,
                        PropertyClient propertyClient, AuthUserClient authUserClient,
                        NotificationClient notificationClient) {
        this.shareService = shareService;
        this.quoteRepository = quoteRepository;
        this.interestRepository = interestRepository;
        this.agentRepository = agentRepository;
        this.dossierClient = dossierClient;
        this.propertyClient = propertyClient;
        this.authUserClient = authUserClient;
        this.notificationClient = notificationClient;
    }

    // ── agent side ───────────────────────────────────────────────────────────

    /** Gate B + active consent; relays damage-service's integrity recompute. */
    @Transactional(readOnly = true)
    public DossierVerifyView verify(AuthUser user, UUID dossierId) {
        shareService.requireActiveShare(user, dossierId);
        DossierClient.DossierVerify result = dossierClient.verify(dossierId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Dossier not found"));
        return new DossierVerifyView(dossierId, result.manifestHash(), result.recomputedHash(),
                result.tamperEvident(), result.photoCount(),
                result.mismatches().stream().map(m ->
                        new MismatchView(m.objectPath(), m.expected(), m.actual())).toList(),
                Instant.now());
    }

    @Transactional
    public QuoteCreateResponse create(AuthUser user, UUID dossierId, QuoteCreateRequest request) {
        DossierShare share = shareService.requireActiveShare(user, dossierId);
        PolicyQuote quote = new PolicyQuote();
        quote.setAgentInterestId(share.getAgentInterestId());
        quote.setDossierShareId(share.getId());
        quote.setCoverageAmount(request.coverageAmount());
        quote.setPremium(request.premium());
        quote.setTermMonths(request.termMonths().shortValue());
        quote = quoteRepository.save(quote);

        PolicyQuote saved = quote;
        interestRepository.findById(share.getAgentInterestId()).ifPresent(interest ->
                notificationClient.send(interest.getOwnerUserId(), "QUOTE_ISSUED",
                        "You received an insurance quote",
                        "An agent issued a policy quote on your shared dossier.",
                        Map.of("quoteId", saved.getId().toString())));
        return new QuoteCreateResponse(quote.getId(), quote.getStatus().name());
    }

    // ── owner side ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageEnvelope<OwnerQuoteItem> ownerQuotes(AuthUser user, int page, int size) {
        return PageEnvelope.of(quoteRepository
                .findByInterestOwner(user.id(),
                        PageRequest.of(PageEnvelope.clampPage(page), PageEnvelope.clampSize(size)))
                .map(this::toOwnerItem));
    }

    private OwnerQuoteItem toOwnerItem(PolicyQuote quote) {
        Optional<AgentInterest> interest = interestRepository.findById(quote.getAgentInterestId());
        Optional<InsuranceAgent> agent = interest
                .flatMap(i -> agentRepository.findById(i.getAgentId()));
        return new OwnerQuoteItem(quote.getId(),
                agent.flatMap(a -> authUserClient.byId(a.getUserId()))
                        .map(AuthUserClient.AuthUserInfo::fullName).orElse(null),
                agent.map(InsuranceAgent::getInsurerName).orElse(null),
                interest.flatMap(i -> propertyClient.propertyCached(i.getPropertyId()))
                        .map(PropertyClient.PropertyInfo::name).orElse(null),
                quote.getCoverageAmount(), quote.getPremium(), quote.getTermMonths(),
                quote.getStatus().name(), quote.getCreatedAt());
    }

    @Transactional
    public QuoteRespondResponse respond(AuthUser user, UUID quoteId, boolean accept) {
        PolicyQuote quote = quoteRepository.findById(quoteId).orElse(null);
        AgentInterest interest = quote == null ? null
                : interestRepository.findById(quote.getAgentInterestId()).orElse(null);
        // owner mismatch is a 404, not a 403 — quotes must not be enumerable
        if (interest == null || !interest.getOwnerUserId().equals(user.id())) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Quote not found");
        }
        if (quote.getStatus() != QuoteStatus.PENDING) {
            throw new ApiException(ErrorCode.ALREADY_RESPONDED, "Quote already responded to");
        }
        quote.setStatus(accept ? QuoteStatus.ACCEPTED : QuoteStatus.DECLINED);
        quote.setRespondedAt(Instant.now());
        quoteRepository.save(quote);

        if (accept) {
            // the billable referral event — revenue reconciliation greps this line
            log.info("REFERRAL quote accepted: quoteId={} agentId={} interestId={} "
                            + "coverageAmount={} premium={} termMonths={}",
                    quote.getId(), interest.getAgentId(), interest.getId(),
                    quote.getCoverageAmount(), quote.getPremium(), quote.getTermMonths());
        }
        agentRepository.findById(interest.getAgentId()).ifPresent(agent ->
                notificationClient.send(agent.getUserId(), "QUOTE_RESPONSE",
                        accept ? "Your quote was accepted" : "Your quote was declined",
                        accept ? "The owner accepted your policy quote."
                                : "The owner declined your policy quote.",
                        Map.of("quoteId", quote.getId().toString(),
                                "status", quote.getStatus().name())));
        return new QuoteRespondResponse(quote.getId(), quote.getStatus().name(),
                quote.getRespondedAt());
    }
}
