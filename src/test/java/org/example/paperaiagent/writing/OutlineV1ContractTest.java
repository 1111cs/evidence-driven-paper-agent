package org.example.paperaiagent.writing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.paperaiagent.agent.runtime.AgentRunCommand;
import org.example.paperaiagent.agent.runtime.AgentRuntimeEvent;
import org.example.paperaiagent.agent.runtime.PaperAgentRuntime;
import org.example.paperaiagent.agent.runtime.PaperToolNames;
import org.example.paperaiagent.knowledge.EvidenceCandidate;
import org.example.paperaiagent.knowledge.KnowledgeSearchResult;
import org.example.paperaiagent.writing.application.WritingConflictException;
import org.example.paperaiagent.writing.application.WritingTaskApplicationService;
import org.example.paperaiagent.writing.application.dto.CreateWritingTaskRequest;
import org.example.paperaiagent.writing.application.dto.GenerateOutlineRequest;
import org.example.paperaiagent.writing.application.dto.OutlineGenerationResponse;
import org.example.paperaiagent.writing.application.dto.ResearchRequest;
import org.example.paperaiagent.writing.evidence.TaskEvidenceAdoptionService;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryOutlineVersionRepository;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryOutlineSectionRepository;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryTaskEvidenceRepository;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryTaskRunRepository;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryWritingTaskRepository;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryWritingTransactionOperations;
import org.example.paperaiagent.writing.outline.OutlineContextSelector;
import org.example.paperaiagent.writing.outline.OutlineStructureExtractor;
import org.example.paperaiagent.writing.outline.OutlineStatus;
import org.example.paperaiagent.writing.run.TaskRunAction;
import org.example.paperaiagent.writing.task.WritingStage;
import org.example.paperaiagent.writing.workflow.StageCapabilityPolicy;
import org.example.paperaiagent.writing.workflow.WritingWorkflow;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OutlineV1ContractTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void generatesVersionWithEmptyToolkitAndOrderedEvidenceThenConfirmsIdempotently() {
        ScriptedRuntime runtime = new ScriptedRuntime();
        WritingTaskApplicationService service = service(runtime, 20, 30000);
        var task = service.createTask(new CreateWritingTaskRequest("ALMT", "ALMT", "evidence based"));
        service.startResearch(task.taskId(), new ResearchRequest("research", "research-1", "session"));

        OutlineGenerationResponse generated = service.generateOutline(task.taskId(),
                new GenerateOutlineRequest("outline-1", "生成方法与实验提纲", "outline-session"));

        assertEquals(TaskRunAction.GENERATE_OUTLINE, generated.run().action());
        assertEquals(OutlineStatus.DRAFT, generated.outline().status());
        assertEquals(1, generated.outline().versionNumber());
        assertEquals(2, generated.outline().evidenceIds().size());
        assertEquals(Set.of(), runtime.commands.get(1).allowedTools());
        assertEquals(WritingStage.OUTLINE_PENDING, service.getTask(task.taskId()).stage());

        var confirmed = service.confirmOutline(task.taskId(), generated.outline().outlineVersionId());
        var repeated = service.confirmOutline(task.taskId(), generated.outline().outlineVersionId());
        assertEquals(OutlineStatus.CONFIRMED, confirmed.status());
        assertEquals(confirmed.outlineVersionId(), repeated.outlineVersionId());
        assertEquals(WritingStage.OUTLINE_CONFIRMED, service.getTask(task.taskId()).stage());
        assertEquals(confirmed.outlineVersionId(), service.getTask(task.taskId()).confirmedOutlineVersionId());
    }

    @Test
    void sameIdempotencyKeyReturnsExistingOutlineAndDifferentContentConflicts() {
        ScriptedRuntime runtime = new ScriptedRuntime();
        WritingTaskApplicationService service = service(runtime, 20, 30000);
        var task = service.createTask(new CreateWritingTaskRequest("ALMT", "ALMT", "R"));
        service.startResearch(task.taskId(), new ResearchRequest("research", "research-1", "session"));
        GenerateOutlineRequest request = new GenerateOutlineRequest("outline-key", "same", null);

        var first = service.generateOutline(task.taskId(), request);
        var second = service.generateOutline(task.taskId(), request);

        assertEquals(first.outline().outlineVersionId(), second.outline().outlineVersionId());
        assertEquals(2, runtime.invocations.get(), "research plus one outline invocation");
        WritingConflictException conflict = assertThrows(WritingConflictException.class,
                () -> service.generateOutline(task.taskId(),
                        new GenerateOutlineRequest("outline-key", "different", null)));
        assertEquals("IDEMPOTENCY_CONFLICT", conflict.code());
    }

    @Test
    void refusesOutlineWithoutEvidence() {
        PaperAgentRuntime direct = command -> Flux.just(completed(1, "research without evidence"));
        WritingTaskApplicationService service = service(direct, 20, 30000);
        var task = service.createTask(new CreateWritingTaskRequest("T", "Topic", "R"));
        service.startResearch(task.taskId(), new ResearchRequest("research", "research-1", null));

        WritingConflictException failure = assertThrows(WritingConflictException.class,
                () -> service.generateOutline(task.taskId(),
                        new GenerateOutlineRequest("outline-1", "outline", null)));
        assertEquals("OUTLINE_EVIDENCE_REQUIRED", failure.code());
        assertEquals(WritingStage.RESEARCHING, service.getTask(task.taskId()).stage());
    }

    @Test
    void evidenceCountBudgetIsAppliedAndRecorded() {
        ScriptedRuntime runtime = new ScriptedRuntime();
        WritingTaskApplicationService service = service(runtime, 1, 30000);
        var task = service.createTask(new CreateWritingTaskRequest("ALMT", "ALMT", "R"));
        service.startResearch(task.taskId(), new ResearchRequest("research", "research-1", null));

        var generated = service.generateOutline(task.taskId(),
                new GenerateOutlineRequest("outline-1", "outline", null));

        assertEquals(1, generated.outline().evidenceIds().size());
        String prompt = runtime.commands.get(1).message();
        assertTrue(prompt.contains("AHL high score"));
        assertFalse(prompt.contains("low score evidence"));
    }

    private WritingTaskApplicationService service(PaperAgentRuntime runtime, int count, int chars) {
        return new WritingTaskApplicationService(
                new InMemoryWritingTaskRepository(), new InMemoryTaskRunRepository(),
                new InMemoryTaskEvidenceRepository(), new InMemoryOutlineVersionRepository(),
                new InMemoryOutlineSectionRepository(), new OutlineStructureExtractor(),
                new OutlineContextSelector(count, chars),
                new WritingWorkflow(new StageCapabilityPolicy()),
                new TaskEvidenceAdoptionService(json), runtime, new InMemoryWritingTransactionOperations());
    }

    private final class ScriptedRuntime implements PaperAgentRuntime {
        private final AtomicInteger invocations = new AtomicInteger();
        private final List<AgentRunCommand> commands = new CopyOnWriteArrayList<>();

        @Override
        public Flux<AgentRuntimeEvent> stream(AgentRunCommand command) {
            commands.add(command);
            int invocation = invocations.incrementAndGet();
            if (invocation == 1) {
                KnowledgeSearchResult result = KnowledgeSearchResult.of("ALMT", 2, List.of(
                        new EvidenceCandidate("chunk-high", "doc", "（4）ALMT", "3 Method",
                                List.of(0, 1), "AHL high score", 0.95),
                        new EvidenceCandidate("chunk-low", "doc", "（4）ALMT", "4 Experiments",
                                List.of(6, 7), "low score evidence", 0.70)));
                return Flux.just(toolCompleted(1, result), completed(2, "research complete"));
            }
            return Flux.just(completed(1, "# 提纲\n## 1 方法 [evidenceId]\n## 2 实验 [evidenceId]"));
        }
    }

    private AgentRuntimeEvent toolCompleted(long sequence, Object structured) {
        return new AgentRuntimeEvent(sequence, Instant.now(), AgentRuntimeEvent.Type.TOOL_COMPLETED,
                PaperToolNames.SEARCH_KNOWLEDGE, "call-" + sequence,
                Map.of("structuredResult", json.convertValue(structured, Object.class), "rawResult", "fixture"));
    }

    private static AgentRuntimeEvent completed(long sequence, String answer) {
        return new AgentRuntimeEvent(sequence, Instant.now(), AgentRuntimeEvent.Type.RUN_COMPLETED,
                null, null, Map.of("finalAnswer", answer));
    }
}
