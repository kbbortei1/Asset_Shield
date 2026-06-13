package com.assetshield.marketplace.config;

import com.assetshield.marketplace.common.ErrorCode;
import com.assetshield.marketplace.security.EnvelopeResponses;
import com.assetshield.marketplace.security.InternalApiKeyFilter;
import com.assetshield.marketplace.security.JwtAuthenticationFilter;
import com.assetshield.marketplace.security.TokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final TokenService tokenService;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public SecurityConfig(TokenService tokenService, AppProperties properties, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain internalChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/internal/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new InternalApiKeyFilter(properties.internalApiKey(), objectMapper),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("INTERNAL"))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint()));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new JwtAuthenticationFilter(tokenService, objectMapper),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Paystack calls this; authentication is the HMAC signature
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhook").permitAll()
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint())
                        .accessDeniedHandler(accessDeniedHandler()));
        return http.build();
    }

    private AuthenticationEntryPoint entryPoint() {
        return (request, response, exception) ->
                EnvelopeResponses.write(response, objectMapper, ErrorCode.TOKEN_INVALID,
                        "Authentication required");
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) ->
                EnvelopeResponses.write(response, objectMapper, ErrorCode.FORBIDDEN,
                        "You do not have permission to perform this action");
    }
}
