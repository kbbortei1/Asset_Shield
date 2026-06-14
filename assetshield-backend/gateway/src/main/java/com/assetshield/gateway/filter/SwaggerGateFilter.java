package com.assetshield.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Hard kill-switch for the aggregated Swagger UI and the proxied /api-docs
 * routes. When SWAGGER_ENABLED=false every documentation path returns 404 as
 * if it never existed — so a public deployment can flip one env var and expose
 * no API surface description at the edge. Runs before edge JWT validation so
 * the docs are never even reached when disabled.
 */
@Component
public class SwaggerGateFilter implements GlobalFilter, Ordered {

    private final boolean enabled;

    public SwaggerGateFilter(@Value("${app.swagger-enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled && isDocPath(exchange.getRequest().getPath().value())) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    private static boolean isDocPath(String path) {
        return path.startsWith("/swagger-ui")
                || path.startsWith("/api-docs")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/webjars");
    }

    @Override
    public int getOrder() {
        return -200; // ahead of JwtAuthGlobalFilter (-100)
    }
}
