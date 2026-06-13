package com.assetshield.gateway.filter;

import com.assetshield.gateway.support.EnvelopeWriter;
import com.assetshield.gateway.support.PublicPaths;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Edge JWT validation. Verifies HS256 signature + expiry; on success forwards
 * X-User-Id / X-User-Role extracted from claims. Downstream services trust
 * these headers only because the gateway is the sole ingress — and they still
 * re-validate the JWT themselves (defense in depth). Client-supplied identity
 * headers are always stripped, including on public paths.
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    private final SecretKey key;
    private final EnvelopeWriter envelope;

    public JwtAuthGlobalFilter(@Value("${app.jwt.secret}") String secret, EnvelopeWriter envelope) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.envelope = envelope;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (PublicPaths.isPublic(path)) {
            ServerHttpRequest stripped = exchange.getRequest().mutate()
                    .headers(h -> {
                        h.remove(USER_ID_HEADER);
                        h.remove(USER_ROLE_HEADER);
                    })
                    .build();
            return chain.filter(exchange.mutate().request(stripped).build());
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return envelope.write(exchange, HttpStatus.UNAUTHORIZED,
                    "TOKEN_INVALID", "Missing or malformed Authorization header");
        }

        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(authorization.substring(7))
                    .getPayload();
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .headers(h -> {
                        h.set(USER_ID_HEADER, claims.getSubject());
                        h.set(USER_ROLE_HEADER, String.valueOf(claims.get("role")));
                    })
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (ExpiredJwtException e) {
            return envelope.write(exchange, HttpStatus.UNAUTHORIZED,
                    "TOKEN_EXPIRED", "Access token has expired");
        } catch (JwtException | IllegalArgumentException e) {
            return envelope.write(exchange, HttpStatus.UNAUTHORIZED,
                    "TOKEN_INVALID", "Access token is invalid");
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
