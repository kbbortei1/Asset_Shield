package com.assetshield.auth.service;

import com.assetshield.auth.common.PageEnvelope;
import com.assetshield.auth.domain.AuditEvent;
import com.assetshield.auth.repo.AuditEventRepository;
import com.assetshield.auth.web.dto.AuthDtos.AuditEventItem;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only security audit. Writes run in their OWN transaction
 * (REQUIRES_NEW) so a failed-login row survives the rollback of the request
 * that produced it — and an audit failure never breaks the main flow.
 */
@Service
public class AuditService {

    // action vocabulary — keep in sync with the admin audit screen
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ACCOUNT_VERIFIED = "ACCOUNT_VERIFIED";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String ADMIN_CREATED = "ADMIN_CREATED";
    public static final String ACCOUNT_PURGED = "ACCOUNT_PURGED";

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorUserId, String action, String target, String detail) {
        try {
            AuditEvent event = new AuditEvent();
            event.setActorUserId(actorUserId);
            event.setAction(action);
            event.setTarget(truncate(target, 120));
            event.setDetail(truncate(detail, 300));
            repository.saveAndFlush(event);
        } catch (RuntimeException e) {
            log.warn("Audit write failed for {}: {}", action, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PageEnvelope<AuditEventItem> list(String action, int page, int size) {
        PageRequest pageable = PageRequest.of(PageEnvelope.clampPage(page),
                PageEnvelope.clampSize(size));
        var events = action == null || action.isBlank()
                ? repository.findAllByOrderByCreatedAtDesc(pageable)
                : repository.findByActionOrderByCreatedAtDesc(action.trim().toUpperCase(), pageable);
        return PageEnvelope.of(events.map(e -> new AuditEventItem(e.getId(), e.getActorUserId(),
                e.getAction(), e.getTarget(), e.getDetail(), e.getCreatedAt())));
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 3) + "...";
    }
}
