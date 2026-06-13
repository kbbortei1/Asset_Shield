package com.assetshield.property.access;

/** Resolved access of a user on a property. */
public enum AccessLevel {
    OWNER,
    MEMBER_EXPORT,
    MEMBER,
    NONE;

    public boolean canView() {
        return this != NONE;
    }

    public boolean isOwner() {
        return this == OWNER;
    }
}
