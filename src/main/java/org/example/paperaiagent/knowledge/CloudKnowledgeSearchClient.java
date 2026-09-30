package org.example.paperaiagent.knowledge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public final class CloudKnowledgeSearchClient implements KnowledgeSearchGateway {

    private final Settings settings;
    private final ObjectMapper objectMapper;
    private final CloudKnowledgeResponseParser parser;
    private final HttpClient httpClient;

    public CloudKnowledgeSearchClient(Settings settings, ObjectMapper objectMapper) {
        this(
                settings,
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(settings.connectTimeout())
                        .build()
        );
    }

    CloudKnowledgeSearchClient(Settings settings, ObjectMapper objectMapper, HttpClient httpClient) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.parser = new CloudKnowledgeResponseParser(objectMapper);
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public KnowledgeSearchResult search(String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            throw new IllegalArgumentException("Knowledge search query must not be blank");
        }

        HttpRequest request = HttpRequest.newBuilder(settings.endpoint())
                .timeout(settings.readTimeout())
                .header("Authorization", "Bearer " + settings.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(normalizedQuery), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            validateStatus(response.statusCode());
            return parser.parse(normalizedQuery, response.body());
        } catch (HttpTimeoutException exception) {
            throw new KnowledgeSearchException(
                    KnowledgeSearchErrorCode.TIMEOUT,
                    "Knowledge search timed out after " + settings.readTimeout(),
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new KnowledgeSearchException(
                    KnowledgeSearchErrorCode.CANCELLED,
                    "Knowledge search was cancelled",
                    exception
            );
        } catch (IOException exception) {
            throw new KnowledgeSearchException(
                    KnowledgeSearchErrorCode.NETWORK_ERROR,
                    "Knowledge search network failure: " + safeMessage(exception),
                    exception
            );
        }
    }

    private String requestBody(String query) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "index_id", settings.indexId(),
                    "query", query,
                    "top_k", settings.topK()
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize knowledge search request", exception);
        }
    }

    private void validateStatus(int status) {
        if (status >= 200 && status < 300) {
            return;
        }
        if (status == 401) {
            throw httpFailure(KnowledgeSearchErrorCode.UNAUTHORIZED, status, "Knowledge service rejected the API key");
        }
        if (status == 403) {
            throw httpFailure(KnowledgeSearchErrorCode.FORBIDDEN, status, "Knowledge service denied access");
        }
        throw httpFailure(KnowledgeSearchErrorCode.REMOTE_ERROR, status,
                "Knowledge service returned HTTP " + status);
    }

    private KnowledgeSearchException httpFailure(KnowledgeSearchErrorCode code, int status, String message) {
        return new KnowledgeSearchException(code, message, status, null);
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    public record Settings(
            URI endpoint,
            String apiKey,
            String indexId,
            int topK,
            Duration connectTimeout,
            Duration readTimeout
    ) {

        public Settings {
            Objects.requireNonNull(endpoint, "endpoint");
            String scheme = endpoint.getScheme();
            if (endpoint.getHost() == null
                    || (!("http".equalsIgnoreCase(scheme)) && !("https".equalsIgnoreCase(scheme)))) {
                throw new IllegalArgumentException("Knowledge endpoint must be an absolute HTTP(S) URI");
            }
            apiKey = requireText(apiKey, "apiKey");
            indexId = requireText(indexId, "indexId");
            if (topK <= 0 || topK > 100) {
                throw new IllegalArgumentException("topK must be between 1 and 100");
            }
            connectTimeout = requirePositive(connectTimeout, "connectTimeout");
            readTimeout = requirePositive(readTimeout, "readTimeout");
        }

        private static String requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value.trim();
        }

        private static Duration requirePositive(Duration value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException(name + " must be greater than zero");
            }
            return value;
        }
    }
}
