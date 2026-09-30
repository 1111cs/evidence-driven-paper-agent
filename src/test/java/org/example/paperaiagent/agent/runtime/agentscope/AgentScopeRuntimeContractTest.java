package org.example.paperaiagent.agent.runtime.agentscope;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolParam;
import org.example.paperaiagent.agent.runtime.AgentRunCommand;
import org.example.paperaiagent.agent.runtime.AgentRunResult;
import org.example.paperaiagent.agent.runtime.AgentRuntimeEvent;
import org.example.paperaiagent.knowledge.EvidenceCandidate;
import org.example.paperaiagent.knowledge.KnowledgeSearchResult;
import org.example.paperaiagent.knowledge.KnowledgeToolAdapter;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentScopeRuntimeContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void directAnswerCompletesOnceWithoutCallingTools() {
        AgentScopeRuntimeAdapter runtime = runtime(
                new ScriptedModel(textResponse("The paper introduces end-to-end object detection.")),
                new Toolkit(),
                3
        );

        AgentRunResult result = run(runtime, "Summarize the supplied statement");

        assertEquals(AgentRunResult.Status.COMPLETED, result.status(), result.toString());
        assertEquals("The paper introduces end-to-end object detection.", result.finalAnswer());
        assertEquals(1, count(result, AgentRuntimeEvent.Type.RUN_COMPLETED));
        assertEquals(0, count(result, AgentRuntimeEvent.Type.TOOL_STARTED));
    }

    @Test
    void executesSearchDownloadParseThenReturnsFinalAnswer() {
        List<String> calls = new CopyOnWriteArrayList<>();
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SuccessfulFakePaperTools(calls));
        ScriptedModel model = new ScriptedModel(
                toolResponse("call-search", "arxiv_search", Map.of("query", "DETR original paper")),
                toolResponse("call-download", "arxiv_download", Map.of("arxivId", "2005.12872")),
                toolResponse("call-parse", "pdf_read", Map.of("filePath", "/tmp/2005.12872.pdf")),
                textResponse("DETR formulates detection as direct set prediction.")
        );

        AgentRunResult result = run(runtime(model, toolkit, 6), "Find and summarize DETR");

        assertEquals(AgentRunResult.Status.COMPLETED, result.status(), result.toString());
        assertEquals("DETR formulates detection as direct set prediction.", result.finalAnswer());
        assertEquals(List.of("search", "download", "parse"), calls);
        assertEquals(
                List.of("arxiv_search", "arxiv_download", "pdf_read"),
                result.events().stream()
                        .filter(event -> event.type() == AgentRuntimeEvent.Type.TOOL_STARTED)
                        .map(AgentRuntimeEvent::toolName)
                        .toList()
        );
        assertEquals(3, count(result, AgentRuntimeEvent.Type.TOOL_COMPLETED));
        assertEquals(1, count(result, AgentRuntimeEvent.Type.RUN_COMPLETED));
    }

    @Test
    void toolExceptionProducesToolFailedAndRunFailed() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new FailingFakePaperTools());
        ScriptedModel model = new ScriptedModel(
                toolResponse("call-search", "arxiv_search", Map.of("query", "DETR")),
                textResponse("This answer must never be reached")
        );

        AgentRunResult result = run(runtime(model, toolkit, 4), "Find DETR");

        assertEquals(AgentRunResult.Status.FAILED, result.status());
        assertEquals("TOOL_FAILED", result.errorCode());
        assertEquals(1, count(result, AgentRuntimeEvent.Type.TOOL_FAILED));
        assertEquals(1, count(result, AgentRuntimeEvent.Type.RUN_FAILED));
        assertEquals(0, count(result, AgentRuntimeEvent.Type.RUN_COMPLETED));
        assertFalse(result.events().stream()
                .anyMatch(event -> "This answer must never be reached".equals(event.data().get("text"))));
    }

    @Test
    void exceedingMaxIterationsIsAnExplicitFailure() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SuccessfulFakePaperTools(new ArrayList<>()));
        AtomicInteger calls = new AtomicInteger();
        Model repeatingModel = new Model() {
            @Override
            public Flux<ChatResponse> stream(
                    List<io.agentscope.core.message.Msg> messages,
                    List<ToolSchema> schemas,
                    GenerateOptions options
            ) {
                return Flux.just(toolResponse(
                        "call-" + calls.incrementAndGet(),
                        "arxiv_search",
                        Map.of("query", "DETR")
                ));
            }

            @Override
            public String getModelName() {
                return "repeating-model";
            }
        };

        AgentRunResult result = run(runtime(repeatingModel, toolkit, 2), "Keep searching forever");

        assertEquals(AgentRunResult.Status.FAILED, result.status());
        assertEquals("MAX_ITERATIONS_EXCEEDED", result.errorCode(), result.toString());
        assertEquals(1, count(result, AgentRuntimeEvent.Type.RUN_FAILED));
        assertEquals(0, count(result, AgentRuntimeEvent.Type.RUN_COMPLETED));
    }

    @Test
    void invokesExplicitKnowledgeSearchAndReturnsSourceGroundedAnswer() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new KnowledgeToolAdapter(query -> KnowledgeSearchResult.of(
                query,
                1,
                List.of(new EvidenceCandidate(
                        "chunk-1",
                        "document-1",
                        "ALMT paper",
                        "3 Method",
                        List.of(2),
                        "AHL suppresses sentiment-irrelevant information.",
                        0.9
                ))
        )));
        ScriptedModel model = new ScriptedModel(
                toolResponse("call-knowledge", "search_knowledge", Map.of("query", "ALMT AHL")),
                textResponse("ALMT uses AHL to suppress irrelevant information (ALMT paper, 3 Method, page 2).")
        );

        AgentRunResult result = run(runtime(model, toolkit, 3), "Explain ALMT using the knowledge base");

        assertEquals(AgentRunResult.Status.COMPLETED, result.status(), result.toString());
        assertEquals(
                List.of("search_knowledge"),
                result.events().stream()
                        .filter(event -> event.type() == AgentRuntimeEvent.Type.TOOL_STARTED)
                        .map(AgentRuntimeEvent::toolName)
                        .toList()
        );
        assertTrue(result.finalAnswer().contains("ALMT paper"));
        AgentRuntimeEvent completed = result.events().stream()
                .filter(event -> event.type() == AgentRuntimeEvent.Type.TOOL_COMPLETED)
                .findFirst()
                .orElseThrow();
        assertTrue(completed.data().get("structuredResult") instanceof Map<?, ?>);
        assertTrue(completed.data().containsKey("rawResult"));
        assertTrue(completed.data().containsKey("arguments"));
    }

    @Test
    void appliesPerRunToolAllowlistWithoutMutatingSharedToolkit() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SuccessfulFakePaperTools(new ArrayList<>()));
        AtomicReference<Set<String>> visibleTools = new AtomicReference<>();
        Model inspectingModel = new Model() {
            @Override
            public Flux<ChatResponse> stream(
                    List<io.agentscope.core.message.Msg> messages,
                    List<ToolSchema> schemas,
                    GenerateOptions options
            ) {
                visibleTools.set(schemas.stream().map(ToolSchema::getName).collect(java.util.stream.Collectors.toSet()));
                return Flux.just(textResponse("Only the permitted tool was visible."));
            }

            @Override
            public String getModelName() {
                return "allowlist-inspector";
            }
        };
        AgentScopeRuntimeAdapter runtime = runtime(inspectingModel, toolkit, 2);

        AgentRunResult result = runtime.run(new AgentRunCommand(
                "Inspect tools",
                "allowlist-session",
                "allowlist-run",
                Set.of("arxiv_search"),
                Map.of()
        )).block(Duration.ofSeconds(10));

        assertNotNull(result);
        assertEquals(AgentRunResult.Status.COMPLETED, result.status());
        assertEquals(Set.of("arxiv_search"), visibleTools.get());
        assertEquals(
                Set.of("arxiv_search", "arxiv_download", "pdf_read"),
                toolkit.getToolNames(),
                "Filtering one run must not mutate the shared toolkit"
        );
    }

    @Test
    void emptyAllowlistCreatesAnActuallyEmptyToolkit() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SuccessfulFakePaperTools(new ArrayList<>()));
        AtomicReference<Set<String>> visibleTools = new AtomicReference<>();
        Model inspectingModel = new Model() {
            @Override
            public Flux<ChatResponse> stream(List<io.agentscope.core.message.Msg> messages,
                                             List<ToolSchema> schemas, GenerateOptions options) {
                visibleTools.set(schemas.stream().map(ToolSchema::getName)
                        .collect(java.util.stream.Collectors.toSet()));
                return Flux.just(textResponse("outline"));
            }

            @Override public String getModelName() { return "empty-toolkit-inspector"; }
        };

        AgentRunResult result = runtime(inspectingModel, toolkit, 2).run(new AgentRunCommand(
                "Generate outline", "empty-session", "empty-run", Set.of(), Map.of())).block(Duration.ofSeconds(10));

        assertNotNull(result);
        assertEquals(AgentRunResult.Status.COMPLETED, result.status());
        assertEquals(Set.of(), visibleTools.get());
        assertEquals(Set.of("arxiv_search", "arxiv_download", "pdf_read"), toolkit.getToolNames());
    }

    @Test
    void modelCannotExecuteToolWhenAllowlistIsEmpty() {
        List<String> calls = new CopyOnWriteArrayList<>();
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SuccessfulFakePaperTools(calls));
        AgentRunResult result = runtime(new ScriptedModel(
                toolResponse("forbidden-search", "arxiv_search", Map.of("query", "ALMT"))), toolkit, 2)
                .run(new AgentRunCommand("No tools", "empty-session", "empty-call-run", Set.of(), Map.of()))
                .block(Duration.ofSeconds(10));

        assertNotNull(result);
        assertEquals(AgentRunResult.Status.FAILED, result.status());
        assertTrue(calls.isEmpty());
    }

    @Test
    void toolOutsideAllowlistIsNotExecuted() {
        List<String> calls = new CopyOnWriteArrayList<>();
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SuccessfulFakePaperTools(calls));
        ScriptedModel model = new ScriptedModel(
                toolResponse("forbidden-download", "arxiv_download", Map.of("arxivId", "2005.12872"))
        );
        AgentScopeRuntimeAdapter runtime = runtime(model, toolkit, 2);

        AgentRunResult result = runtime.run(new AgentRunCommand(
                "Try a forbidden tool",
                "forbidden-session",
                "forbidden-run",
                Set.of("arxiv_search"),
                Map.of()
        )).block(Duration.ofSeconds(10));

        assertNotNull(result);
        assertEquals(AgentRunResult.Status.FAILED, result.status());
        assertTrue(calls.isEmpty(), "The unregistered tool method must never execute");
    }

    @Test
    void concurrentRunsKeepIndependentToolAllowlists() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SuccessfulFakePaperTools(new CopyOnWriteArrayList<>()));
        ConcurrentLinkedQueue<Set<String>> observed = new ConcurrentLinkedQueue<>();
        Model inspectingModel = new Model() {
            @Override
            public Flux<ChatResponse> stream(
                    List<io.agentscope.core.message.Msg> messages,
                    List<ToolSchema> schemas,
                    GenerateOptions options
            ) {
                observed.add(schemas.stream().map(ToolSchema::getName).collect(java.util.stream.Collectors.toSet()));
                return Flux.just(textResponse("done"));
            }

            @Override
            public String getModelName() {
                return "concurrent-allowlist-inspector";
            }
        };
        AgentScopeRuntimeAdapter runtime = runtime(inspectingModel, toolkit, 2);
        AgentRunCommand searchOnly = new AgentRunCommand(
                "search", "session-a", "run-a", Set.of("arxiv_search"), Map.of()
        );
        AgentRunCommand readOnly = new AgentRunCommand(
                "read", "session-b", "run-b", Set.of("pdf_read"), Map.of()
        );

        List<AgentRunResult> results = reactor.core.publisher.Mono.zip(
                        runtime.run(searchOnly).subscribeOn(reactor.core.scheduler.Schedulers.parallel()),
                        runtime.run(readOnly).subscribeOn(reactor.core.scheduler.Schedulers.parallel())
                )
                .map(tuple -> List.of(tuple.getT1(), tuple.getT2()))
                .block(Duration.ofSeconds(10));

        assertNotNull(results);
        assertTrue(results.stream().allMatch(result -> result.status() == AgentRunResult.Status.COMPLETED));
        assertTrue(observed.contains(Set.of("arxiv_search")));
        assertTrue(observed.contains(Set.of("pdf_read")));
        assertEquals(Set.of("arxiv_search", "arxiv_download", "pdf_read"), toolkit.getToolNames());
    }

    private AgentScopeRuntimeAdapter runtime(Model model, Toolkit toolkit, int maxIterations) {
        return new AgentScopeRuntimeAdapter(model, toolkit, maxIterations, new ObjectMapper());
    }

    private AgentRunResult run(AgentScopeRuntimeAdapter runtime, String message) {
        AgentRunResult result = runtime.run(new AgentRunCommand(message, "contract-test"))
                .block(Duration.ofSeconds(10));
        assertNotNull(result);
        assertTrue(result.events().stream().map(AgentRuntimeEvent::sequence).toList()
                .equals(java.util.stream.LongStream.rangeClosed(1, result.events().size()).boxed().toList()));
        return result;
    }

    private long count(AgentRunResult result, AgentRuntimeEvent.Type type) {
        return result.events().stream().filter(event -> event.type() == type).count();
    }

    private static ChatResponse textResponse(String text) {
        return response(List.of(TextBlock.builder().text(text).build()), "stop");
    }

    private static ChatResponse toolResponse(String id, String name, Map<String, Object> input) {
        ToolUseBlock toolUse = ToolUseBlock.builder()
                .id(id)
                .name(name)
                .input(input)
                .content(toJson(input))
                .build();
        return response(List.of(toolUse), "tool_calls");
    }

    private static String toJson(Map<String, Object> input) {
        try {
            return JSON.writeValueAsString(input);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to serialize fake tool input", exception);
        }
    }

    private static ChatResponse response(List<ContentBlock> content, String finishReason) {
        return ChatResponse.builder()
                .id("response-" + System.nanoTime())
                .content(content)
                .finishReason(finishReason)
                .build();
    }

    private static final class ScriptedModel implements Model {

        private final Deque<ChatResponse> responses;

        private ScriptedModel(ChatResponse... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        @Override
        public Flux<ChatResponse> stream(
                List<io.agentscope.core.message.Msg> messages,
                List<ToolSchema> tools,
                GenerateOptions options
        ) {
            if (responses.isEmpty()) {
                return Flux.error(new AssertionError("Fake model received more calls than scripted"));
            }
            return Flux.just(responses.removeFirst());
        }

        @Override
        public String getModelName() {
            return "scripted-model";
        }
    }

    public static final class SuccessfulFakePaperTools {

        private final List<String> calls;

        public SuccessfulFakePaperTools(List<String> calls) {
            this.calls = calls;
        }

        @Tool(name = "arxiv_search", description = "fake search")
        public String search(@ToolParam(name = "query") String query) {
            calls.add("search");
            return "2005.12872";
        }

        @Tool(name = "arxiv_download", description = "fake download")
        public String download(@ToolParam(name = "arxivId") String arxivId) {
            calls.add("download");
            return "/tmp/2005.12872.pdf";
        }

        @Tool(name = "pdf_read", description = "fake parse")
        public String parse(@ToolParam(name = "filePath") String filePath) {
            calls.add("parse");
            return "DETR evidence";
        }
    }

    public static final class FailingFakePaperTools {

        @Tool(name = "arxiv_search", description = "fake failing search")
        public String search(@ToolParam(name = "query") String query) {
            throw new IllegalStateException("search backend unavailable");
        }
    }
}
