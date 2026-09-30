package org.example.paperaiagent.agent.runtime;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record AgentRunCommand(
        String message,
        @JsonAlias("conversationId") String sessionId,
        String runId,
        Set<String> allowedTools,
        Map<String, Object> metadata
) {

    public AgentRunCommand {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        message = message.trim();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        if (runId == null || runId.isBlank()) {
            runId = UUID.randomUUID().toString();
        }
        allowedTools = allowedTools == null ? null : Set.copyOf(allowedTools);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * Compatibility constructor used by the existing /agent/run and /manus/chat endpoints.
     * A null allowedTools value means that the caller did not request filtering.
     */
    public AgentRunCommand(String message, String conversationId) {
        this(message, conversationId, null, null, Map.of());
    }

    /**
     * Compatibility accessor for existing runtime code and API clients.
     */
    public String conversationId() {
        return sessionId;
    }
}
