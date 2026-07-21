package com.assetshield.auth.domain;

/** What a user's problem report is about (mirrors the DB CHECK). */
public enum ReportCategory {
    BUG,
    PAYMENT,
    ACCOUNT,
    SUGGESTION,
    OTHER
}
