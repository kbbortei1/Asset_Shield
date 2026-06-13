package com.assetshield.gateway.filter;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Ensures every request carries an X-Request-Id, forwarded and logged. */
@Component
public class RequestIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Request-Id";

    private static final Logger log = LoggerFactory.getLogger(RequestIdGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
        String requestId = (incoming == null || incoming.isBlank()) ? UUID.randomUUID().toString() : incoming;

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(h -> h.set(HEADER, requestId))
                .build();
        exchange.getResponse().getHeaders().set(HEADER, requestId);

        ServerWebExchange mutatedExchange = exchange.mutate().request(mutated).build();
        long startNanos = System.nanoTime();
        return chain.filter(mutatedExchange).doFinally(signal -> {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("{} {} -> {} [requestId={}] {}ms",
                    mutated.getMethod(),
                    mutated.getPath().value(),
                    mutatedExchange.getResponse().getStatusCode(),
                    requestId,
                    elapsedMs);
        });
    }

    @Override
    public int getOrder() {
        return -300;
    }
}
