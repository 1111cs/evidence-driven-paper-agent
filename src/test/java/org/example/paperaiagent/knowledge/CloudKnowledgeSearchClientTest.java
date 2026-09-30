package org.example.paperaiagent.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudKnowledgeSearchClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private HttpServer server;
    private ExecutorService executor;
    private URI baseUri;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/success", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, fixture());
        });
        server.createContext("/empty", exchange -> respond(
                exchange,
                200,
                "{\"success\":true,\"data\":{\"total\":0,\"nodes\":[]}}"
        ));
        server.createContext("/unauthorized", exchange -> respond(exchange, 401, "{}"));
        server.createContext("/forbidden", exchange -> respond(exchange, 403, "{}"));
        server.createContext("/malformed", exchange -> respond(exchange, 200, "{\"data\":{}}"));
        server.createContext("/slow", this::respondSlowly);
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void postsConfiguredIndexQueryAndTopKWithBearerToken() throws Exception {
        KnowledgeSearchResult result = client("/success", Duration.ofSeconds(2)).search("  ALMT modules  ");

        assertEquals(KnowledgeSearchResult.Status.RESULTS, result.status());
        assertEquals("Bearer test-key", authorization.get());
        JsonNode body = objectMapper.readTree(requestBody.get());
        assertEquals("test-index", body.path("index_id").textValue());
        assertEquals("ALMT modules", body.path("query").textValue());
        assertEquals(5, body.path("top_k").intValue());
    }

    @Test
    void returnsNoResultsWithoutThrowing() {
        KnowledgeSearchResult result = client("/empty", Duration.ofSeconds(2)).search("unknown paper");

        assertEquals(KnowledgeSearchResult.Status.NO_RESULTS, result.status());
    }

    @Test
    void distinguishesUnauthorized() {
        assertError("/unauthorized", KnowledgeSearchErrorCode.UNAUTHORIZED, 401);
    }

    @Test
    void distinguishesForbidden() {
        assertError("/forbidden", KnowledgeSearchErrorCode.FORBIDDEN, 403);
    }

    @Test
    void distinguishesMalformedSuccessResponse() {
        KnowledgeSearchException exception = assertThrows(
                KnowledgeSearchException.class,
                () -> client("/malformed", Duration.ofSeconds(2)).search("ALMT")
        );

        assertEquals(KnowledgeSearchErrorCode.MALFORMED_RESPONSE, exception.code());
    }

    @Test
    void distinguishesReadTimeout() {
        KnowledgeSearchException exception = assertThrows(
                KnowledgeSearchException.class,
                () -> client("/slow", Duration.ofMillis(50)).search("ALMT")
        );

        assertEquals(KnowledgeSearchErrorCode.TIMEOUT, exception.code());
    }

    private void assertError(String path, KnowledgeSearchErrorCode code, int status) {
        KnowledgeSearchException exception = assertThrows(
                KnowledgeSearchException.class,
                () -> client(path, Duration.ofSeconds(2)).search("ALMT")
        );
        assertEquals(code, exception.code());
        assertEquals(status, exception.httpStatus());
    }

    private CloudKnowledgeSearchClient client(String path, Duration readTimeout) {
        return new CloudKnowledgeSearchClient(
                new CloudKnowledgeSearchClient.Settings(
                        baseUri.resolve(path),
                        "test-key",
                        "test-index",
                        5,
                        Duration.ofSeconds(1),
                        readTimeout
                ),
                objectMapper
        );
    }

    private void respondSlowly(HttpExchange exchange) throws IOException {
        try {
            Thread.sleep(300);
            respond(exchange, 200, "{\"success\":true,\"data\":{\"total\":0,\"nodes\":[]}}");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            exchange.close();
        }
    }

    private String fixture() throws IOException {
        try (var input = getClass().getResourceAsStream("/fixtures/bailian/knowledge-search-success.json")) {
            if (input == null) {
                throw new IOException("Fixture was not found");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
