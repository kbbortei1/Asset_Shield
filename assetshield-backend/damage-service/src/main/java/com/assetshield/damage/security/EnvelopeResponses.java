package com.assetshield.damage.security;

import com.assetshield.damage.common.ApiResponse;
import com.assetshield.damage.common.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

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
