package com.assetshield.auth.service;

import com.assetshield.auth.client.NotificationClient;
import com.assetshield.auth.common.ApiException;
import com.assetshield.auth.common.ErrorCode;
import com.assetshield.auth.common.PageEnvelope;
import com.assetshield.auth.domain.BroadcastAudience;
import com.assetshield.auth.domain.Role;
import com.assetshield.auth.domain.UserStatus;
import com.assetshield.auth.repo.UserRepository;
import com.assetshield.auth.web.dto.AuthDtos.AdminUserItem;
import com.assetshield.auth.web.dto.AuthDtos.AudienceCountsResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin broadcasts: preview reach, search the user directory, and fan a
 * notification out to a segment or a hand-picked set. Admins never handle raw
 * phone numbers — they pick from names.
 */
@Service
public class AdminBroadcastService {

    /** Broadcastable roles — admins are never a broadcast target. */
    private static final List<Role> AUDIENCE_ROLES = List.of(Role.OWNER, Role.AGENT);

    private final UserRepository userRepository;
    private final NotificationClient notificationClient;

    public AdminBroadcastService(UserRepository userRepository, NotificationClient notificationClient) {
        this.userRepository = userRepository;
        this.notificationClient = notificationClient;
    }

    @Transactional(readOnly = true)
    public AudienceCountsResponse counts() {
        long owners = userRepository.countByStatusAndRoleInAndDeletedAtIsNull(
                UserStatus.ACTIVE, List.of(Role.OWNER));
        long agents = userRepository.countByStatusAndRoleInAndDeletedAtIsNull(
                UserStatus.ACTIVE, List.of(Role.AGENT));
        return new AudienceCountsResponse(owners + agents, owners, agents);
    }

    @Transactional(readOnly = true)
    public PageEnvelope<AdminUserItem> search(String query, int page, int size) {
        String q = query == null ? "" : query.trim();
        return PageEnvelope.of(userRepository
                .searchDirectory(UserStatus.ACTIVE, AUDIENCE_ROLES, q,
                        PageRequest.of(PageEnvelope.clampPage(page), PageEnvelope.clampSize(size)))
                .map(u -> new AdminUserItem(u.getId(), u.getFullName(), u.getPhoneNumber(),
                        u.getRole().name())));
    }

    /** Resolves the audience to recipient ids and dispatches. Returns the reach. */
    @Transactional(readOnly = true)
    public int broadcast(BroadcastAudience audience, List<UUID> userIds, String title, String body) {
        List<UUID> recipients = switch (audience) {
            case EVERYONE -> userRepository.idsByStatusAndRoles(UserStatus.ACTIVE, AUDIENCE_ROLES);
            case OWNERS -> userRepository.idsByStatusAndRoles(UserStatus.ACTIVE, List.of(Role.OWNER));
            case AGENTS -> userRepository.idsByStatusAndRoles(UserStatus.ACTIVE, List.of(Role.AGENT));
            case SPECIFIC -> userIds == null ? List.of() : userIds.stream().distinct().toList();
        };
        if (recipients.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "This broadcast has no recipients");
        }
        notificationClient.broadcast(recipients, title.trim(), body.trim());
        return recipients.size();
    }
}
