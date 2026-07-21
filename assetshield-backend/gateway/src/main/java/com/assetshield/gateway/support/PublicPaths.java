package com.assetshield.gateway.support;

import java.util.List;
import java.util.Set;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Paths that bypass edge JWT validation. Includes future public paths
 * (payments webhook, shared dossiers) so later days only add routes.
 */
public final class PublicPaths {

    /** Public auth paths that are also subject to per-IP rate limiting. */
    public static final Set<String> RATE_LIMITED = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/register-agent",
            "/api/v1/auth/login",
            "/api/v1/auth/verify-otp",
            "/api/v1/auth/resend-otp",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/refresh");

    private static final List<PathPattern> PATTERNS;

    static {
        PathPatternParser parser = new PathPatternParser();
        PATTERNS = List.of(
                parser.parse("/api/v1/auth/register"),
                parser.parse("/api/v1/auth/register-agent"),
                parser.parse("/api/v1/auth/login"),
                parser.parse("/api/v1/auth/verify-otp"),
                parser.parse("/api/v1/auth/resend-otp"),
                parser.parse("/api/v1/auth/forgot-password"),
                parser.parse("/api/v1/auth/reset-password"),
                parser.parse("/api/v1/auth/refresh"),
                parser.parse("/actuator/health"),
                parser.parse("/api/v1/payments/webhook"),
                parser.parse("/api/v1/dossiers/shared/**"),
                // Local storage provider downloads (token-gated per service)
                parser.parse("/api/v1/public/files/**"),
                parser.parse("/api/v1/public/damage-files/**"),
                parser.parse("/api/v1/public/user-files/**"),
                // Aggregated Swagger UI + proxied docs (dev only; further gated
                // by SWAGGER_ENABLED via SwaggerGateFilter).
                parser.parse("/swagger-ui.html"),
                parser.parse("/swagger-ui/**"),
                parser.parse("/webjars/**"),
                parser.parse("/v3/api-docs/**"),
                parser.parse("/api-docs/**"));
    }

    private PublicPaths() {
    }

    public static boolean isPublic(String path) {
        PathContainer container = PathContainer.parsePath(path);
        return PATTERNS.stream().anyMatch(p -> p.matches(container));
    }

    public static boolean isRateLimited(String path) {
        return RATE_LIMITED.contains(path);
    }
}
