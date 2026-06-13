package com.assetshield.gateway;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** Tiny in-process HTTP server standing in for auth-service. */
public final class StubDownstream {

    private final HttpServer server;
    private volatile Headers lastRequestHeaders;
    private volatile String lastPath;

    private StubDownstream(HttpServer server) {
        this.server = server;
    }

    public static StubDownstream start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            StubDownstream stub = new StubDownstream(server);
            server.createContext("/", exchange -> {
                stub.lastRequestHeaders = exchange.getRequestHeaders();
                stub.lastPath = exchange.getRequestURI().getPath();
                byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return stub;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public String uri() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public Headers lastRequestHeaders() {
        return lastRequestHeaders;
    }

    public String lastPath() {
        return lastPath;
    }

    /** Forget any previously recorded request. */
    public void reset() {
        lastRequestHeaders = null;
        lastPath = null;
    }

    public void stop() {
        server.stop(0);
    }
}
