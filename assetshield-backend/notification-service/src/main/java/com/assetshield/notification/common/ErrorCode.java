package com.assetshield.notification.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
