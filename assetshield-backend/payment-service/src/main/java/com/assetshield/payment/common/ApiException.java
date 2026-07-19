package com.assetshield.payment.common;

import java.util.Map;

public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Map<String, String> fields;

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public ApiException(ErrorCode errorCode, String message, Map<String, String> fields) {
        super(message);
        this.errorCode = errorCode;
        this.fields = fields;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, String> fields() {
        return fields;
    }
}
