package com.assetshield.damage;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

/** Mints access tokens exactly as auth-service does (HS256, sub/role/phone). */
public final class TestTokens {

    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(TestProps.JWT_SECRET.getBytes(StandardCharsets.UTF_8));

    private TestTokens() {
    }

    public static String token(UUID userId, String role, String phone) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .claim("phone", phone)
                .issuedAt(new Date(now))
                .expiration(new Date(now + 3_600_000))
                .signWith(KEY, Jwts.SIG.HS256)
                .compact();
    }

    public static String bearer(UUID userId, String phone) {
        return "Bearer " + token(userId, "OWNER", phone);
    }
}
