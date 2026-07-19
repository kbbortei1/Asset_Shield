package com.assetshield.payment.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    AGENT_NOT_VERIFIED(HttpStatus.FORBIDDEN),
    SUBSCRIPTION_INACTIVE(HttpStatus.FORBIDDEN),
    NOT_OWNER(HttpStatus.FORBIDDEN),
    SHARE_REVOKED(HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    DUPLICATE_PENDING_INTEREST(HttpStatus.CONFLICT),
    ALREADY_DECIDED(HttpStatus.CONFLICT),
    ALREADY_RESPONDED(HttpStatus.CONFLICT),
    ALREADY_SHARED(HttpStatus.CONFLICT),
    LICENCE_EXISTS(HttpStatus.CONFLICT),
    PAYMENT_INIT_FAILED(HttpStatus.BAD_GATEWAY),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
