package com.assetshield.notification.domain;

/** The full cross-service notification vocabulary (mirrors the DB CHECK). */
public enum NotificationType {
    TIP,
    REDOC_REMINDER,
    DOSSIER_READY,
    AGENT_INTEREST,
    INTEREST_RESPONSE,
    INTEREST_REVOKED,
    SHARE_CREATED,
    SHARE_REVOKED,
    QUOTE_ISSUED,
    QUOTE_RESPONSE,
    SUBSCRIPTION_EXPIRY,
    HOUSEHOLD_INVITE,
    AGENT_VERIFIED,
    AGENT_REJECTED,
    MAINTENANCE_DUE
}
