package com.assetshield.gateway.filter;

import com.assetshield.gateway.support.EnvelopeWriter;
import com.assetshield.gateway.support.PublicPaths;
import com.assetshield.gateway.support.TokenBucket;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * In-memory per-IP token bucket on the public auth paths only
 * (30 requests/minute by default). Deliberately not Redis-backed.
 */
@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final int MAX_TRACKED_IPS = 10_000;

    private final EnvelopeWriter envelope;
    private final int capacity;
    private final int refillPerMinute;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitGlobalFilter(EnvelopeWriter envelope,
                                 @Value("${app.rate-limit.capacity:30}") int capacity,
                                 @Value("${app.rate-limit.refill-per-minute:30}") int refillPerMinute) {
        this.envelope = envelope;
        this.capacity = capacity;
        this.refillPerMinute = refillPerMinute;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!PublicPaths.isRateLimited(path)) {
            return chain.filter(exchange);
        }

        if (buckets.size() > MAX_TRACKED_IPS) {
            buckets.clear();
        }
        TokenBucket bucket = buckets.computeIfAbsent(clientIp(exchange),
                ip -> new TokenBucket(capacity, refillPerMinute));
        if (!bucket.tryConsume()) {
            return envelope.write(exchange, HttpStatus.TOO_MANY_REQUESTS,
                    "RATE_LIMITED", "Too many requests. Try again shortly.");
        }
        return chain.filter(exchange);
    }

    private String clientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote != null ? remote.getAddress().getHostAddress() : "unknown";
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
