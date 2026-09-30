package org.example.paperaiagent.agent.runtime;

import java.time.Instant;
import java.util.Map;

public record AgentRuntimeEvent(
        long sequence,
        Instant occurredAt,
        Type type,
        String toolName,
        String toolCallId,
        Map<String, Object> data
) {

    public AgentRuntimeEvent {
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public enum Type {
        TEXT_DELTA,
        TOOL_STARTED,
        TOOL_COMPLETED,
        TOOL_FAILED,
        RUN_COMPLETED,
        RUN_FAILED
    }
}
