package com.assetshield.auth.security;

import com.assetshield.auth.common.ApiException;
import com.assetshield.auth.token.TokenService;
import tools.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Re-validates the JWT even though the gateway already did (defense in depth).
 * Principal is the user id (UUID); authority is ROLE_&lt;role claim&gt;.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(TokenService tokenService, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = tokenService.parse(header.substring(7));
                UUID userId = UUID.fromString(claims.getSubject());
                String role = String.valueOf(claims.get("role"));
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ApiException e) {
                EnvelopeResponses.write(response, objectMapper, e.errorCode(), e.getMessage());
                return;
            } catch (IllegalArgumentException e) {
                EnvelopeResponses.write(response, objectMapper,
                        com.assetshield.auth.common.ErrorCode.TOKEN_INVALID, "Access token is invalid");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
