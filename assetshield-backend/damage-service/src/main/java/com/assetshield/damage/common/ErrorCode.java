package com.assetshield.damage.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    HASH_MISMATCH(HttpStatus.BAD_REQUEST),
    INVALID_STATE_TRANSITION(HttpStatus.BAD_REQUEST),
    EMPTY_REPORT(HttpStatus.BAD_REQUEST),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    PAYMENT_REQUIRED(HttpStatus.PAYMENT_REQUIRED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_OWNER(HttpStatus.FORBIDDEN),
    NOT_MEMBER(HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    DUPLICATE_PHOTO_HASH(HttpStatus.CONFLICT),
    PAIR_EXISTS(HttpStatus.CONFLICT),
    DOSSIER_EXISTS(HttpStatus.CONFLICT),
    GENERATION_IN_PROGRESS(HttpStatus.CONFLICT),
    GENERATION_FAILED(HttpStatus.CONFLICT),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
