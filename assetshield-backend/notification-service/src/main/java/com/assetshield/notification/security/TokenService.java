package com.assetshield.notification.security;

import com.assetshield.notification.common.ApiException;
import com.assetshield.notification.common.ErrorCode;
import com.assetshield.notification.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Validates HS256 access tokens issued by auth-service (defense in depth: the
 * gateway already verified them at the edge). Parse-only — this service never
 * issues tokens.
 */
@Service
public class TokenService {

    private final SecretKey key;

    public TokenService(AppProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
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
