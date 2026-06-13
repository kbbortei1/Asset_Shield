package com.assetshield.damage.web;

import com.assetshield.damage.common.ApiException;
import com.assetshield.damage.common.ErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Parses + validates the JSON "metadata" multipart part. Taking it as a raw
 * string keeps clients working even when they omit the part's content type.
 */
@Component
public class MetadataParser {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public MetadataParser(ObjectMapper objectMapper, Validator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public <T> T parse(String json, Class<T> type) {
        T value;
        try {
            value = objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Malformed metadata JSON");
        }
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            Map<String, String> fields = new LinkedHashMap<>();
            for (ConstraintViolation<T> violation : violations) {
                fields.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage());
            }
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Validation failed", fields);
        }
        return value;
    }
}
