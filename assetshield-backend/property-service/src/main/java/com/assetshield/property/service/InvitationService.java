package com.assetshield.property.service;

import com.assetshield.property.access.PropertyAccessService;
import com.assetshield.property.client.AuthUserClient;
import com.assetshield.property.client.NotificationClient;
import com.assetshield.property.common.ApiException;
import com.assetshield.property.common.ErrorCode;
import com.assetshield.property.domain.HouseholdInvitation;
import com.assetshield.property.domain.HouseholdMembership;
import com.assetshield.property.domain.InvitationStatus;
import com.assetshield.property.domain.Property;
import com.assetshield.property.repo.HouseholdInvitationRepository;
import com.assetshield.property.repo.HouseholdMembershipRepository;
import com.assetshield.property.repo.PropertyRepository;
import com.assetshield.property.security.AuthUser;
import com.assetshield.property.web.dto.PropertyDtos.InviteRequest;
import com.assetshield.property.web.dto.PropertyDtos.InviteResponse;
import com.assetshield.property.web.dto.PropertyDtos.MemberItem;
import com.assetshield.property.web.dto.PropertyDtos.MyInvitationItem;
import com.assetshield.property.web.dto.PropertyDtos.RespondRequest;
import com.assetshield.property.web.dto.PropertyDtos.RespondResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationService {

    private static final Duration INVITE_TTL = Duration.ofDays(7);

    private final HouseholdInvitationRepository invitationRepository;
    private final HouseholdMembershipRepository membershipRepository;
    private final PropertyRepository propertyRepository;
    private final PropertyAccessService accessService;
    private final AuthUserClient authUserClient;
    private final NotificationClient notificationClient;

    public InvitationService(HouseholdInvitationRepository invitationRepository,
                             HouseholdMembershipRepository membershipRepository,
                             PropertyRepository propertyRepository,
                             PropertyAccessService accessService,
                             AuthUserClient authUserClient,
                             NotificationClient notificationClient) {
        this.invitationRepository = invitationRepository;
        this.membershipRepository = membershipRepository;
        this.propertyRepository = propertyRepository;
        this.accessService = accessService;
        this.authUserClient = authUserClient;
        this.notificationClient = notificationClient;
    }

    @Transactional
    public InviteResponse invite(AuthUser user, UUID propertyId, InviteRequest request) {
        Property property = accessService.requireOwner(propertyId, user.id());
        if (request.inviteePhone().equals(user.phone())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "You cannot invite your own phone number");
        }

        Optional<AuthUserClient.AuthUserInfo> invitee = authUserClient.byPhone(request.inviteePhone());
        if (invitee.isPresent() && membershipRepository.existsByPropertyIdAndMemberUserIdAndRevokedAtIsNull(
                propertyId, invitee.get().id())) {
            throw new ApiException(ErrorCode.ALREADY_MEMBER,
                    "This user is already an active member of the property");
        }

        Instant now = Instant.now();
        Optional<HouseholdInvitation> pending = invitationRepository
                .findByPropertyIdAndInviteePhoneAndStatus(propertyId, request.inviteePhone(),
                        InvitationStatus.PENDING);
        if (pending.isPresent()) {
            if (pending.get().getExpiresAt().isAfter(now)) {
                throw new ApiException(ErrorCode.DUPLICATE_PENDING_INVITE,
                        "A pending invitation already exists for this phone number");
            }
            // Lapsed but still PENDING: retire it so the partial unique index
            // accepts the fresh invite.
            pending.get().setStatus(InvitationStatus.EXPIRED);
            invitationRepository.saveAndFlush(pending.get());
        }

        HouseholdInvitation invitation = new HouseholdInvitation();
        invitation.setPropertyId(propertyId);
        invitation.setInvitedByUserId(user.id());
        invitation.setInviteePhone(request.inviteePhone());
        invitation.setCanExport(request.canExport());
        invitation.setExpiresAt(now.plus(INVITE_TTL));
        invitee.ifPresent(info -> invitation.setInviteeUserId(info.id()));
        HouseholdInvitation saved = invitationRepository.save(invitation);

        invitee.ifPresent(info -> notificationClient.send(info.id(), "HOUSEHOLD_INVITE",
                "Household invitation",
                "You have been invited to help document \"" + property.getName() + "\"",
                Map.of("invitationId", saved.getId().toString(),
                        "propertyId", propertyId.toString())));

        return new InviteResponse(saved.getId(), saved.getStatus().name(), saved.getExpiresAt(),
                invitee.isPresent());
    }

    @Transactional(readOnly = true)
    public List<MyInvitationItem> myInvitations(AuthUser user) {
        List<HouseholdInvitation> invitations =
                invitationRepository.findPendingFor(user.id(), user.phone(), Instant.now());
        Map<UUID, Property> properties = invitations.isEmpty() ? Map.of()
                : propertyRepository.findAllById(invitations.stream()
                                .map(HouseholdInvitation::getPropertyId).distinct().toList()).stream()
                        .collect(Collectors.toMap(Property::getId, Function.identity()));
        return invitations.stream()
                .filter(i -> {
                    Property p = properties.get(i.getPropertyId());
                    return p != null && p.getDeletedAt() == null;
                })
                .map(i -> new MyInvitationItem(i.getId(),
                        properties.get(i.getPropertyId()).getName(),
                        ownerName(properties.get(i.getPropertyId()).getOwnerUserId()),
                        i.isCanExport(), i.getExpiresAt()))
                .toList();
    }

    @Transactional
    public RespondResponse respond(AuthUser user, UUID invitationId, RespondRequest request) {
        HouseholdInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Invitation not found"));
        boolean addressee = user.id().equals(invitation.getInviteeUserId())
                || user.phone().equals(invitation.getInviteePhone());
        if (!addressee) {
            throw new ApiException(ErrorCode.FORBIDDEN, "This invitation is not addressed to you");
        }
        if (invitation.getStatus() != InvitationStatus.PENDING
                || invitation.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.ALREADY_RESPONDED,
                    "This invitation has already been responded to or has expired");
        }

        if (invitation.getInviteeUserId() == null) {
            invitation.setInviteeUserId(user.id());
        }
        invitation.setRespondedAt(Instant.now());

        if (!request.accept()) {
            invitation.setStatus(InvitationStatus.DECLINED);
            invitationRepository.save(invitation);
            return new RespondResponse(invitation.getStatus().name(), null);
        }

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);
        HouseholdMembership membership = new HouseholdMembership();
        membership.setPropertyId(invitation.getPropertyId());
        membership.setMemberUserId(user.id());
        membership.setGrantedByUserId(invitation.getInvitedByUserId());
        membership.setCanExport(invitation.isCanExport());
        HouseholdMembership saved = membershipRepository.save(membership);
        return new RespondResponse(invitation.getStatus().name(), saved.getId());
    }

    @Transactional(readOnly = true)
    public List<MemberItem> members(AuthUser user, UUID propertyId) {
        accessService.requireOwner(propertyId, user.id());
        return membershipRepository.findByPropertyIdAndRevokedAtIsNullOrderByCreatedAtAsc(propertyId).stream()
                .map(m -> {
                    Optional<AuthUserClient.AuthUserInfo> info = authUserClient.byId(m.getMemberUserId());
                    return new MemberItem(m.getId(), m.getMemberUserId(),
                            info.map(AuthUserClient.AuthUserInfo::fullName).orElse("Unknown"),
                            info.map(AuthUserClient.AuthUserInfo::phoneNumber).orElse("unknown"),
                            m.isCanExport(), m.getCreatedAt());
                })
                .toList();
    }

    @Transactional
    public Map<String, Boolean> removeMember(AuthUser user, UUID propertyId, UUID memberUserId) {
        accessService.requireOwner(propertyId, user.id());
        HouseholdMembership membership = membershipRepository
                .findByPropertyIdAndMemberUserIdAndRevokedAtIsNull(propertyId, memberUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "No active membership for this user on this property"));
        membership.setRevokedAt(Instant.now());
        membershipRepository.save(membership);
        return Map.of("removed", true);
    }

    private String ownerName(UUID ownerUserId) {
        return authUserClient.byId(ownerUserId)
                .map(AuthUserClient.AuthUserInfo::fullName)
                .orElse("Unknown");
    }
}
