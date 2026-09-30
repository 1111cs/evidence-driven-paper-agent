package org.example.paperaiagent.agent.runtime;

import java.util.List;

public record AgentRunResult(
        String conversationId,
        Status status,
        String finalAnswer,
        String errorCode,
        String errorMessage,
        List<AgentRuntimeEvent> events
) {

    public AgentRunResult {
        events = events == null ? List.of() : List.copyOf(events);
    }

    public static AgentRunResult fromEvents(String conversationId, List<AgentRuntimeEvent> events) {
        if (events == null || events.isEmpty()) {
            return new AgentRunResult(
                    conversationId,
                    Status.FAILED,
                    null,
                    "EMPTY_EVENT_STREAM",
                    "Agent runtime completed without emitting a terminal event",
                    List.of()
            );
        }

        AgentRuntimeEvent terminal = events.get(events.size() - 1);
        if (terminal.type() == AgentRuntimeEvent.Type.RUN_COMPLETED) {
            return new AgentRunResult(
                    conversationId,
                    Status.COMPLETED,
                    valueAsString(terminal, "finalAnswer"),
                    null,
                    null,
                    events
            );
        }
        if (terminal.type() == AgentRuntimeEvent.Type.RUN_FAILED) {
            return new AgentRunResult(
                    conversationId,
                    Status.FAILED,
                    null,
                    valueAsString(terminal, "errorCode"),
                    valueAsString(terminal, "message"),
                    events
            );
        }
        return new AgentRunResult(
                conversationId,
                Status.FAILED,
                null,
                "MISSING_TERMINAL_EVENT",
                "Agent runtime event stream did not end with RUN_COMPLETED or RUN_FAILED",
                events
        );
    }

    private static String valueAsString(AgentRuntimeEvent event, String key) {
        Object value = event.data().get(key);
        return value == null ? null : value.toString();
    }

    public enum Status {
        COMPLETED,
        FAILED
    }
}
