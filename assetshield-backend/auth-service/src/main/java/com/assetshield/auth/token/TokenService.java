package com.assetshield.auth.token;

import com.assetshield.auth.common.ApiException;
import com.assetshield.auth.common.ErrorCode;
import com.assetshield.auth.config.AppProperties;
import com.assetshield.auth.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Issues and validates HS256 access tokens (TTL 1 hour by default). */
@Service
public class TokenService {

    private final SecretKey key;
    private final long accessTtlSeconds;

    public TokenService(AppProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.accessTtlSeconds = properties.jwt().accessTtlSeconds();
    }

    public long accessTtlSeconds() {
        return accessTtlSeconds;
    }

    public String issueAccessToken(User user) {
        return issueAccessToken(user, accessTtlSeconds);
    }

    public String issueAccessToken(User user, long ttlSeconds) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("phone", user.getPhoneNumber())
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlSeconds * 1000))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED, "Access token has expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApiException(ErrorCode.TOKEN_INVALID, "Access token is invalid");
        }
    }
}
