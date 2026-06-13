package com.assetshield.auth.security;

import com.assetshield.auth.common.ApiResponse;
import com.assetshield.auth.common.ErrorCode;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;

/** Writes envelope-shaped rejections from inside the security filter chain. */
public final class EnvelopeResponses {

    private EnvelopeResponses() {
    }

    public static void write(HttpServletResponse response, ObjectMapper objectMapper,
                             ErrorCode errorCode, String message) throws IOException {
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.error(errorCode, message, null));
    }
}
