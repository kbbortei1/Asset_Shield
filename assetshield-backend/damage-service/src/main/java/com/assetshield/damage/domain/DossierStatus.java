package com.assetshield.damage.domain;

/**
 * PENDING_PAYMENT → GENERATING → READY | FAILED. The only trigger for
 * GENERATING is the internal payment-confirmed call; FAILED → GENERATING is
 * allowed via retry (payment already settled).
 */
public enum DossierStatus {
    PENDING_PAYMENT,
    GENERATING,
    READY,
    FAILED
}
