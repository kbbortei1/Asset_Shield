package com.assetshield.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

public final class TestTokens {

    public static final String SECRET =
            "test-only-jwt-secret-0123456789abcdef0123456789abcdef0123456789abcdef";

    private TestTokens() {
    }

    public static String token(UUID userId, String role, long ttlSeconds) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .claim("phone", "+233200000001")
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlSeconds * 1000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }
}
