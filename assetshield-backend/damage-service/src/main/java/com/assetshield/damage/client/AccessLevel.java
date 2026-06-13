package com.assetshield.damage.client;

/** Access of a user on a property, as resolved by property-service. */
public enum AccessLevel {
    OWNER,
    MEMBER_EXPORT,
    MEMBER,
    NONE;

    public boolean canView() {
        return this != NONE;
    }

    /** Report mutations (create, photos, pairs, complete) need export rights. */
    public boolean canMutate() {
        return this == OWNER || this == MEMBER_EXPORT;
    }
}
