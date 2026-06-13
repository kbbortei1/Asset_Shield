package com.assetshield.notification.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps every failure to the standard envelope. Never leaks stack traces or
 * exception class names to clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleApi(ApiException e) {
        return ResponseEntity.status(e.errorCode().status())
                .body(ApiResponse.error(e.errorCode(), e.getMessage(), e.fields()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED, "Validation failed", fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleUnreadable(Exception e) {
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED, "Malformed request body", null));
    }

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleNotFound(Exception e) {
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.status())
                .body(ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found", null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleIntegrity(DataIntegrityViolationException e) {
        log.error("Data integrity violation", e);
        return internalError();
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleOptimisticLock(OptimisticLockingFailureException e) {
        log.warn("Optimistic lock conflict: {}", e.getMessage());
        return internalError();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return internalError();
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> internalError() {
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "Something went wrong", null));
    }
}
