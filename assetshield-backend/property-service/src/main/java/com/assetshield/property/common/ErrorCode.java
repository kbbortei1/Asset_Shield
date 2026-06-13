package com.assetshield.property.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    HASH_MISMATCH(HttpStatus.BAD_REQUEST),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_OWNER(HttpStatus.FORBIDDEN),
    NOT_MEMBER(HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    DUPLICATE_ASSET_HASH(HttpStatus.CONFLICT),
    DUPLICATE_PENDING_INVITE(HttpStatus.CONFLICT),
    ALREADY_RESPONDED(HttpStatus.CONFLICT),
    ALREADY_MEMBER(HttpStatus.CONFLICT),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    FREE_TIER_LIMIT(HttpStatus.UNPROCESSABLE_ENTITY),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
