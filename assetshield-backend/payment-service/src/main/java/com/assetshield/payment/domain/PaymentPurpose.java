package com.assetshield.payment.domain;

public enum PaymentPurpose {
    PRO_SUBSCRIPTION("PRO"),
    DOSSIER_FEE("DSR"),
    AGENT_SUBSCRIPTION("SUB");

    private final String referencePrefix;

    PaymentPurpose(String referencePrefix) {
        this.referencePrefix = referencePrefix;
    }

    public String referencePrefix() {
        return referencePrefix;
    }
}
