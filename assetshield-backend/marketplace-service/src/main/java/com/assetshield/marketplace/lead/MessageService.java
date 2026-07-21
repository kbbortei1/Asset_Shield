package com.assetshield.marketplace.lead;

import com.assetshield.marketplace.client.NotificationClient;
import com.assetshield.marketplace.common.ApiException;
import com.assetshield.marketplace.common.ErrorCode;
import com.assetshield.marketplace.common.PageEnvelope;
import com.assetshield.marketplace.domain.AgentInterest;
import com.assetshield.marketplace.domain.InsuranceAgent;
import com.assetshield.marketplace.domain.InterestMessage;
import com.assetshield.marketplace.domain.InterestStatus;
import com.assetshield.marketplace.repo.AgentInterestRepository;
import com.assetshield.marketplace.repo.InsuranceAgentRepository;
import com.assetshield.marketplace.repo.InterestMessageRepository;
import com.assetshield.marketplace.security.AuthUser;
import com.assetshield.marketplace.web.dto.MarketplaceDtos.MessageItem;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner<->agent chat, scoped to an ACCEPTED agent-interest. Only the two
 * participants can read or post; every call re-checks membership.
 */
@Service
public class MessageService {

    private final InterestMessageRepository messageRepository;
    private final AgentInterestRepository interestRepository;
    private final InsuranceAgentRepository agentRepository;
    private final NotificationClient notificationClient;

    public MessageService(InterestMessageRepository messageRepository,
                          AgentInterestRepository interestRepository,
                          InsuranceAgentRepository agentRepository,
                          NotificationClient notificationClient) {
        this.messageRepository = messageRepository;
        this.interestRepository = interestRepository;
        this.agentRepository = agentRepository;
        this.notificationClient = notificationClient;
    }

    private record Participant(String senderRole, UUID recipientUserId) {
    }

    @Transactional(readOnly = true)
    public PageEnvelope<MessageItem> list(AuthUser user, UUID interestId, int page, int size) {
        AgentInterest interest = requireActive(interestId);
        participant(user, interest); // authorization
        return PageEnvelope.of(messageRepository
                .findByAgentInterestIdOrderByCreatedAtAsc(interestId,
                        PageRequest.of(PageEnvelope.clampPage(page), PageEnvelope.clampSize(size)))
                .map(m -> new MessageItem(m.getId(), m.getSenderUserId(), m.getSenderRole(),
                        m.getBody(), m.getCreatedAt())));
    }

    @Transactional
    public MessageItem send(AuthUser user, UUID interestId, String body) {
        AgentInterest interest = requireActive(interestId);
        Participant me = participant(user, interest);

        InterestMessage message = new InterestMessage();
        message.setAgentInterestId(interestId);
        message.setSenderUserId(user.id());
        message.setSenderRole(me.senderRole());
        message.setBody(body.trim());
        InterestMessage saved = messageRepository.saveAndFlush(message);

        notify(me.recipientUserId(), me.senderRole(), body.trim(), interestId);
        return new MessageItem(saved.getId(), saved.getSenderUserId(), saved.getSenderRole(),
                saved.getBody(), saved.getCreatedAt());
    }

    private AgentInterest requireActive(UUID interestId) {
        AgentInterest interest = interestRepository.findById(interestId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Conversation not found"));
        if (interest.getStatus() != InterestStatus.ACCEPTED) {
            throw new ApiException(ErrorCode.FORBIDDEN, "This connection is not active");
        }
        return interest;
    }

    /** Resolves the caller's role in the thread, or 403 if they're neither party. */
    private Participant participant(AuthUser user, AgentInterest interest) {
        UUID agentUserId = agentRepository.findById(interest.getAgentId())
                .map(InsuranceAgent::getUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Agent not found"));
        if (user.id().equals(interest.getOwnerUserId())) {
            return new Participant("OWNER", agentUserId);
        }
        if (user.id().equals(agentUserId)) {
            return new Participant("AGENT", interest.getOwnerUserId());
        }
        throw new ApiException(ErrorCode.FORBIDDEN, "You are not part of this conversation");
    }

    private void notify(UUID recipientUserId, String senderRole, String body, UUID interestId) {
        String from = "OWNER".equals(senderRole) ? "the property owner" : "an insurance agent";
        String preview = body.length() > 140 ? body.substring(0, 137) + "..." : body;
        try {
            notificationClient.send(recipientUserId, "CHAT_MESSAGE", "New message",
                    "New message from " + from + ": " + preview,
                    Map.of("interestId", interestId.toString(), "deepLink", "chat/" + interestId));
        } catch (RuntimeException e) {
            // a notification hiccup must never fail the message itself
        }
    }
}
