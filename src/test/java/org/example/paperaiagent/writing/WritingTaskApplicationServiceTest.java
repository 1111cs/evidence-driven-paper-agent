package org.example.paperaiagent.writing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.paperaiagent.agent.runtime.AgentRunCommand;
import org.example.paperaiagent.agent.runtime.AgentRuntimeEvent;
import org.example.paperaiagent.agent.runtime.PaperAgentRuntime;
import org.example.paperaiagent.agent.runtime.PaperToolNames;
import org.example.paperaiagent.knowledge.EvidenceCandidate;
import org.example.paperaiagent.knowledge.KnowledgeSearchResult;
import org.example.paperaiagent.writing.application.WritingTaskApplicationService;
import org.example.paperaiagent.writing.application.dto.CreateWritingTaskRequest;
import org.example.paperaiagent.writing.application.dto.ResearchRequest;
import org.example.paperaiagent.writing.application.dto.TaskRunResponse;
import org.example.paperaiagent.writing.application.dto.WritingTaskResponse;
import org.example.paperaiagent.writing.evidence.TaskEvidenceAdoptionService;
import org.example.paperaiagent.writing.evidence.PageNumberingScheme;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryTaskEvidenceRepository;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryTaskRunRepository;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryWritingTaskRepository;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryOutlineVersionRepository;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryOutlineSectionRepository;
import org.example.paperaiagent.writing.outline.OutlineContextSelector;
import org.example.paperaiagent.writing.outline.OutlineStructureExtractor;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryWritingTransactionOperations;
import org.example.paperaiagent.writing.run.TaskRunStatus;
import org.example.paperaiagent.writing.task.WritingStage;
import org.example.paperaiagent.writing.workflow.StageCapabilityPolicy;
import org.example.paperaiagent.writing.workflow.WritingWorkflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WritingTaskApplicationServiceTest {

    private ObjectMapper objectMapper;
    private InMemoryTaskEvidenceRepository evidenceRepository;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        evidenceRepository = new InMemoryTaskEvidenceRepository();
    }

    @Test
    void createsTaskRunsResearchAndAdoptsKnowledgeEvidenceOnce() {
        KnowledgeSearchResult knowledge = KnowledgeSearchResult.of(
                "ALMT AHL",
                1,
                List.of(new EvidenceCandidate(
                        "chunk-1", "doc-1", "ALMT paper", "3 Method", List.of(2),
                        "AHL suppresses sentiment-irrelevant information.", 0.91
                ))
        );
        List<AgentRuntimeEvent> runtimeEvents = List.of(
                toolCompleted(1, PaperToolNames.SEARCH_KNOWLEDGE, knowledge),
                toolCompleted(2, PaperToolNames.SEARCH_KNOWLEDGE, knowledge),
                runCompleted(3, "ALMT uses AHL (ALMT paper, 3 Method, page 2).")
        );
        CapturingRuntime runtime = new CapturingRuntime(runtimeEvents);
        WritingTaskApplicationService service = service(runtime);

        WritingTaskResponse task = service.createTask(
                new CreateWritingTaskRequest("ALMT study", "ALMT", "Use traceable evidence")
        );
        assertEquals(WritingStage.CREATED, task.stage());

        TaskRunResponse run = service.startResearch(
                task.taskId(),
                new ResearchRequest("Explain ALMT AHL", "request-1", "session-1")
        );

        assertEquals(TaskRunStatus.SUCCEEDED, run.status());
        assertEquals(1, run.adoptedEvidenceCount());
        assertEquals(WritingStage.RESEARCHING, service.getTask(task.taskId()).stage());
        assertEquals(1, service.listEvidence(task.taskId()).size());
        assertEquals("ALMT paper", service.listEvidence(task.taskId()).getFirst().documentName());
        assertEquals(List.of(2), service.listEvidence(task.taskId()).getFirst().sourcePages());
        assertEquals(PageNumberingScheme.BAILIAN_RAW,
                service.listEvidence(task.taskId()).getFirst().pageNumberingScheme());
        assertEquals("", service.listEvidence(task.taskId()).getFirst().pageDisplayText());
        assertEquals(PaperToolNames.RESEARCH_TOOLS, runtime.lastCommand.get().allowedTools());
        assertEquals(run.runId(), runtime.lastCommand.get().runId());
    }

    @Test
    void retryWithSameRequestIdReturnsExistingRunWithoutStartingAgentAgain() {
        CapturingRuntime runtime = new CapturingRuntime(List.of(runCompleted(1, "done")));
        WritingTaskApplicationService service = service(runtime);
        WritingTaskResponse task = service.createTask(new CreateWritingTaskRequest("T", "Topic", "R"));
        ResearchRequest request = new ResearchRequest("Research", "same-request", null);

        TaskRunResponse first = service.startResearch(task.taskId(), request);
        TaskRunResponse second = service.startResearch(task.taskId(), request);

        assertEquals(first.runId(), second.runId());
        assertEquals(1, runtime.invocations.get());
    }

    @Test
    void failedAgentCreatesFailedRunAndLeavesTaskResearching() {
        CapturingRuntime runtime = new CapturingRuntime(List.of(new AgentRuntimeEvent(
                1,
                Instant.now(),
                AgentRuntimeEvent.Type.RUN_FAILED,
                null,
                null,
                Map.of("errorCode", "TOOL_FAILED", "message", "knowledge backend unavailable")
        )));
        WritingTaskApplicationService service = service(runtime);
        WritingTaskResponse task = service.createTask(new CreateWritingTaskRequest("T", "Topic", "R"));

        TaskRunResponse run = service.startResearch(
                task.taskId(), new ResearchRequest("Research", "failure-1", null)
        );

        assertEquals(TaskRunStatus.FAILED, run.status());
        assertEquals("TOOL_ERROR", run.errorCode());
        assertEquals(WritingStage.RESEARCHING, service.getTask(task.taskId()).stage());
    }

    @Test
    void arxivResultsAndCandidatesWithoutLocatorAreNotAdopted() {
        KnowledgeSearchResult invalidLocator = KnowledgeSearchResult.of(
                "query",
                1,
                List.of(new EvidenceCandidate(
                        "chunk-2", "doc-2", "Document", "", List.of(), "Content", 0.8
                ))
        );
        CapturingRuntime runtime = new CapturingRuntime(List.of(
                toolCompleted(1, PaperToolNames.ARXIV_SEARCH, Map.of("paperId", "2005.12872")),
                toolCompleted(2, PaperToolNames.SEARCH_KNOWLEDGE, invalidLocator),
                runCompleted(3, "done")
        ));
        WritingTaskApplicationService service = service(runtime);
        WritingTaskResponse task = service.createTask(new CreateWritingTaskRequest("T", "Topic", "R"));

        TaskRunResponse run = service.startResearch(
                task.taskId(), new ResearchRequest("Research", "no-evidence", null)
        );

        assertEquals(TaskRunStatus.SUCCEEDED, run.status());
        assertEquals(0, run.adoptedEvidenceCount());
        assertTrue(service.listEvidence(task.taskId()).isEmpty());
    }

    @Test
    void rejectsSecondConcurrentRunForSameTask() throws Exception {
        CountDownLatch enteredRuntime = new CountDownLatch(1);
        CountDownLatch releaseRuntime = new CountDownLatch(1);
        PaperAgentRuntime blockingRuntime = command -> {
            enteredRuntime.countDown();
            try {
                if (!releaseRuntime.await(5, TimeUnit.SECONDS)) {
                    return Flux.error(new IllegalStateException("test runtime release timed out"));
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return Flux.error(exception);
            }
            return Flux.just(runCompleted(1, "done"));
        };
        WritingTaskApplicationService service = service(blockingRuntime);
        WritingTaskResponse task = service.createTask(new CreateWritingTaskRequest("T", "Topic", "R"));

        CompletableFuture<TaskRunResponse> first = CompletableFuture.supplyAsync(() -> service.startResearch(
                task.taskId(), new ResearchRequest("first", "concurrent-1", null)
        ));
        assertTrue(enteredRuntime.await(5, TimeUnit.SECONDS));

        assertThrows(
                org.example.paperaiagent.writing.application.ResearchRunConflictException.class,
                () -> service.startResearch(
                        task.taskId(), new ResearchRequest("second", "concurrent-2", null)
                )
        );

        releaseRuntime.countDown();
        assertEquals(TaskRunStatus.SUCCEEDED, first.get(5, TimeUnit.SECONDS).status());
    }

    private WritingTaskApplicationService service(PaperAgentRuntime runtime) {
        return new WritingTaskApplicationService(
                new InMemoryWritingTaskRepository(),
                new InMemoryTaskRunRepository(),
                evidenceRepository,
                new InMemoryOutlineVersionRepository(),
                new InMemoryOutlineSectionRepository(),
                new OutlineStructureExtractor(),
                new OutlineContextSelector(20, 30000),
                new WritingWorkflow(new StageCapabilityPolicy()),
                new TaskEvidenceAdoptionService(objectMapper),
                runtime,
                new InMemoryWritingTransactionOperations()
        );
    }

    private AgentRuntimeEvent toolCompleted(long sequence, String toolName, Object result) {
        Object structured = objectMapper.convertValue(result, Object.class);
        return new AgentRuntimeEvent(
                sequence,
                Instant.now(),
                AgentRuntimeEvent.Type.TOOL_COMPLETED,
                toolName,
                "call-" + sequence,
                Map.of(
                        "arguments", Map.of("query", "ALMT"),
                        "structuredResult", structured,
                        "rawResult", "fixture"
                )
        );
    }

    private static AgentRuntimeEvent runCompleted(long sequence, String answer) {
        return new AgentRuntimeEvent(
                sequence,
                Instant.now(),
                AgentRuntimeEvent.Type.RUN_COMPLETED,
                null,
                null,
                Map.of("finalAnswer", answer)
        );
    }

    private static final class CapturingRuntime implements PaperAgentRuntime {

        private final List<AgentRuntimeEvent> events;
        private final AtomicReference<AgentRunCommand> lastCommand = new AtomicReference<>();
        private final AtomicInteger invocations = new AtomicInteger();

        private CapturingRuntime(List<AgentRuntimeEvent> events) {
            this.events = events;
        }

        @Override
        public Flux<AgentRuntimeEvent> stream(AgentRunCommand command) {
            lastCommand.set(command);
            invocations.incrementAndGet();
            return Flux.fromIterable(events);
        }
    }
}
