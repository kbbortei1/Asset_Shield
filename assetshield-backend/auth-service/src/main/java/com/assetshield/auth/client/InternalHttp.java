package com.assetshield.auth.client;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * Shared HTTP timeouts for service-to-service calls. Without these a downed or
 * slow downstream holds request threads forever and the failure cascades;
 * with them the call fails fast and the caller's own resilience (reconciler
 * retries, log-and-continue notifications) takes over.
 */
public final class InternalHttp {

    private InternalHttp() {
    }

    /** Internal mesh calls: connect 3s, read 15s. */
    public static ClientHttpRequestFactory requestFactory() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return factory;
    }

    /** External providers over the public internet: connect 5s, read 20s. */
    public static ClientHttpRequestFactory externalRequestFactory() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(20));
        return factory;
    }
}
