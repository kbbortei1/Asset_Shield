package com.assetshield.payment.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.LinkedHashMap;
import java.util.Map;

/** Standard envelope: every endpoint returns {status, data, message}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String status, T data, String message) {

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>("success", data, message);
    }

    public static ApiResponse<Map<String, Object>> error(ErrorCode errorCode, String message,
                                                         Map<String, String> fields) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("errorCode", errorCode.name());
        if (fields != null && !fields.isEmpty()) {
            data.put("fields", fields);
        }
        return new ApiResponse<>("error", data, message);
    }
}
