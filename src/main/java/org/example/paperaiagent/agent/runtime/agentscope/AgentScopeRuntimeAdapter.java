package org.example.paperaiagent.agent.runtime.agentscope;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.ExceedMaxItersEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import org.example.paperaiagent.agent.runtime.AgentRunCommand;
import org.example.paperaiagent.agent.runtime.AgentRuntimeEvent;
import org.example.paperaiagent.agent.runtime.PaperAgentRuntime;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AgentScopeRuntimeAdapter implements PaperAgentRuntime {

    static final String SYSTEM_PROMPT = """
            You are EvidenceDrivenPaperAgent, a research-paper assistant.
            Use tools when the request requires evidence from a paper.
            When the configured knowledge base may already contain relevant material, use
            search_knowledge and cite its document name, section, and source page indexes.
            For paper discovery, call arxiv_search first. To inspect a paper, pass its arXiv ID to
            arxiv_download, then pass the returned local path to pdf_read. Base the final answer on
            the tool results. When no tool is needed, answer directly. Never invent tool results.
            """;

    private final Model model;
    private final Toolkit toolkit;
    private final int maxIterations;
    private final Duration toolTimeout;
    private final ObjectMapper objectMapper;

    public AgentScopeRuntimeAdapter(Model model, Toolkit toolkit, int maxIterations, ObjectMapper objectMapper) {
        this(model, toolkit, maxIterations, ExecutionConfig.TOOL_DEFAULTS.getTimeout(), objectMapper);
    }

    public AgentScopeRuntimeAdapter(
            Model model,
            Toolkit toolkit,
            int maxIterations,
            Duration toolTimeout,
            ObjectMapper objectMapper
    ) {
        if (maxIterations <= 0) {
            throw new IllegalArgumentException("maxIterations must be greater than zero");
        }
        if (toolTimeout == null || toolTimeout.isZero() || toolTimeout.isNegative()) {
            throw new IllegalArgumentException("toolTimeout must be greater than zero");
        }
        this.model = model;
        this.toolkit = toolkit;
        this.maxIterations = maxIterations;
        this.toolTimeout = toolTimeout;
        this.objectMapper = objectMapper;
    }

    @Override
    public Flux<AgentRuntimeEvent> stream(AgentRunCommand command) {
        return Flux.defer(() -> {
            Toolkit runToolkit = toolkitFor(command.allowedTools());
            ReActAgent agent = ReActAgent.builder()
                    .name("paper_agent")
                    .sysPrompt(SYSTEM_PROMPT)
                    .model(model)
                    .toolkit(runToolkit)
                    .maxIters(maxIterations)
                    .toolExecutionConfig(ExecutionConfig.builder().timeout(toolTimeout).build())
                    .enableMetaTool(false)
                    .build();
            RuntimeContext context = RuntimeContext.builder()
                    .sessionId(command.sessionId())
                    .userId(command.sessionId())
                    .build();
            EventTranslationState state = new EventTranslationState(objectMapper);

            Flux<AgentRuntimeEvent> translated = agent.streamEvents(command.message(), context)
                    .concatMap(event -> Flux.fromIterable(state.translate(event)))
                    .takeUntil(ignored -> state.hasFatalFailure());

            return translated
                    .concatWith(Flux.defer(() -> Flux.just(state.terminalEvent())))
                    .onErrorResume(exception -> Flux.just(state.unexpectedFailure(exception)))
                    .doFinally(signal -> agent.close());
        });
    }

    private Toolkit toolkitFor(Set<String> allowedTools) {
        Toolkit runToolkit = toolkit.copy();
        if (allowedTools == null) {
            return runToolkit;
        }

        // An allowlist is a maximum capability set. Optional tools that are not configured
        // are simply unavailable; they must not make an otherwise valid run fail.
        Set.copyOf(runToolkit.getToolNames()).stream()
                .filter(toolName -> !allowedTools.contains(toolName))
                .forEach(runToolkit::removeTool);
        return runToolkit;
    }

    private static final class EventTranslationState {

        private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

        private final ObjectMapper objectMapper;
        private final Map<String, String> toolNames = new LinkedHashMap<>();
        private final Map<String, StringBuilder> toolArguments = new LinkedHashMap<>();
        private final Map<String, StringBuilder> toolResults = new LinkedHashMap<>();
        private long sequence;
        private String finalAnswer;
        private String failureCode;
        private String failureMessage;
        private boolean terminalEmitted;

        private EventTranslationState(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        private List<AgentRuntimeEvent> translate(AgentEvent event) {
            List<AgentRuntimeEvent> events = new ArrayList<>(1);
            if (event instanceof TextBlockDeltaEvent textDelta) {
                events.add(next(AgentRuntimeEvent.Type.TEXT_DELTA, null, null,
                        Map.of("text", textDelta.getDelta())));
            } else if (event instanceof ToolCallStartEvent toolStart) {
                toolNames.put(toolStart.getToolCallId(), toolStart.getToolCallName());
                toolArguments.put(toolStart.getToolCallId(), new StringBuilder());
            } else if (event instanceof ToolCallDeltaEvent toolDelta) {
                toolNames.putIfAbsent(toolDelta.getToolCallId(), toolDelta.getToolCallName());
                toolArguments.computeIfAbsent(toolDelta.getToolCallId(), ignored -> new StringBuilder())
                        .append(toolDelta.getDelta());
            } else if (event instanceof ToolCallEndEvent toolEnd) {
                String callId = toolEnd.getToolCallId();
                String name = toolEnd.getToolCallName();
                toolNames.put(callId, name);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("arguments", parseArguments(toolArguments.get(callId)));
                events.add(next(AgentRuntimeEvent.Type.TOOL_STARTED, name, callId, data));
            } else if (event instanceof ToolResultTextDeltaEvent resultDelta) {
                toolResults.computeIfAbsent(resultDelta.getToolCallId(), ignored -> new StringBuilder())
                        .append(resultDelta.getDelta());
            } else if (event instanceof ToolResultEndEvent resultEnd) {
                String callId = resultEnd.getToolCallId();
                String name = resultEnd.getToolCallName();
                String output = text(toolResults.get(callId));
                if (resultEnd.getState() == ToolResultState.SUCCESS) {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("arguments", parseArguments(toolArguments.get(callId)));
                    data.put("rawResult", output);
                    // Keep the old key while clients migrate to rawResult.
                    data.put("result", output);
                    Object structuredResult = parseStructuredResult(output);
                    if (structuredResult != null) {
                        data.put("structuredResult", structuredResult);
                    }
                    events.add(next(AgentRuntimeEvent.Type.TOOL_COMPLETED, name, callId, data));
                } else {
                    failureCode = "TOOL_FAILED";
                    failureMessage = output.isBlank()
                            ? "Tool " + name + " ended with state " + resultEnd.getState()
                            : output;
                    events.add(next(AgentRuntimeEvent.Type.TOOL_FAILED, name, callId,
                            Map.of("message", failureMessage, "state", resultEnd.getState().getValue())));
                }
            } else if (event instanceof ExceedMaxItersEvent maxItersEvent) {
                failureCode = "MAX_ITERATIONS_EXCEEDED";
                failureMessage = "Agent exceeded max iterations: " + maxItersEvent.getCurrentIter()
                        + "/" + maxItersEvent.getMaxIters();
            } else if (event instanceof AgentResultEvent resultEvent && resultEvent.getResult() != null) {
                finalAnswer = resultEvent.getResult().getTextContent();
            }
            return events;
        }

        private boolean hasFatalFailure() {
            return failureCode != null;
        }

        private AgentRuntimeEvent terminalEvent() {
            if (terminalEmitted) {
                throw new IllegalStateException("Terminal event was already emitted");
            }
            terminalEmitted = true;
            if (failureCode != null) {
                return next(AgentRuntimeEvent.Type.RUN_FAILED, null, null,
                        Map.of("errorCode", failureCode, "message", failureMessage));
            }
            if (finalAnswer == null || finalAnswer.isBlank()) {
                return next(AgentRuntimeEvent.Type.RUN_FAILED, null, null,
                        Map.of(
                                "errorCode", "MISSING_FINAL_ANSWER",
                                "message", "AgentScope completed without a final answer"
                        ));
            }
            return next(AgentRuntimeEvent.Type.RUN_COMPLETED, null, null,
                    Map.of("finalAnswer", finalAnswer));
        }

        private AgentRuntimeEvent unexpectedFailure(Throwable exception) {
            if (terminalEmitted) {
                return next(AgentRuntimeEvent.Type.RUN_FAILED, null, null,
                        Map.of("errorCode", "EVENT_STREAM_FAILED", "message", safeMessage(exception)));
            }
            terminalEmitted = true;
            return next(AgentRuntimeEvent.Type.RUN_FAILED, null, null,
                    Map.of("errorCode", "AGENTSCOPE_RUNTIME_ERROR", "message", safeMessage(exception)));
        }

        private AgentRuntimeEvent next(
                AgentRuntimeEvent.Type type,
                String toolName,
                String toolCallId,
                Map<String, Object> data
        ) {
            return new AgentRuntimeEvent(++sequence, Instant.now(), type, toolName, toolCallId, data);
        }

        private Object parseArguments(StringBuilder json) {
            String value = text(json);
            if (value.isBlank()) {
                return Map.of();
            }
            try {
                return objectMapper.readValue(value, MAP_TYPE);
            } catch (Exception ignored) {
                return value;
            }
        }

        private Object parseStructuredResult(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                Object parsed = objectMapper.readValue(value, Object.class);
                return parsed instanceof Map<?, ?> || parsed instanceof List<?> ? parsed : null;
            } catch (Exception ignored) {
                return null;
            }
        }

        private static String text(StringBuilder value) {
            return value == null ? "" : value.toString();
        }

        private static String safeMessage(Throwable throwable) {
            String message = throwable.getMessage();
            return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
        }
    }
}
