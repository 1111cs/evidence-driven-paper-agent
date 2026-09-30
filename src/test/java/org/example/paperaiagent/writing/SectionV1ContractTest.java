package org.example.paperaiagent.writing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.paperaiagent.agent.runtime.AgentRunCommand;
import org.example.paperaiagent.agent.runtime.AgentRuntimeEvent;
import org.example.paperaiagent.agent.runtime.PaperAgentRuntime;
import org.example.paperaiagent.agent.runtime.PaperToolNames;
import org.example.paperaiagent.knowledge.EvidenceCandidate;
import org.example.paperaiagent.knowledge.KnowledgeSearchResult;
import org.example.paperaiagent.writing.application.SectionWritingApplicationService;
import org.example.paperaiagent.writing.application.WritingConflictException;
import org.example.paperaiagent.writing.application.WritingHashes;
import org.example.paperaiagent.writing.application.WritingTaskApplicationService;
import org.example.paperaiagent.writing.application.dto.CreateWritingTaskRequest;
import org.example.paperaiagent.writing.application.dto.GenerateOutlineRequest;
import org.example.paperaiagent.writing.application.dto.GenerateSectionRequest;
import org.example.paperaiagent.writing.application.dto.ResearchRequest;
import org.example.paperaiagent.writing.evidence.TaskEvidenceAdoptionService;
import org.example.paperaiagent.writing.evidence.TaskEvidenceId;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryOutlineSectionRepository;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryClaimCitationRepository;
import org.example.paperaiagent.writing.infrastructure.memory.InMemorySectionClaimRepository;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryOutlineVersionRepository;
import org.example.paperaiagent.writing.infrastructure.memory.InMemorySectionVersionRepository;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryTaskEvidenceRepository;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryTaskRunRepository;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryWritingTaskRepository;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryWritingTransactionOperations;
import org.example.paperaiagent.writing.outline.OutlineContextSelector;
import org.example.paperaiagent.writing.outline.OutlineEvidenceReference;
import org.example.paperaiagent.writing.outline.OutlineStructureExtractor;
import org.example.paperaiagent.writing.outline.OutlineStructureInvalidException;
import org.example.paperaiagent.writing.outline.OutlineVersion;
import org.example.paperaiagent.writing.run.TaskRun;
import org.example.paperaiagent.writing.run.TaskRunAction;
import org.example.paperaiagent.writing.run.TaskRunId;
import org.example.paperaiagent.writing.run.TaskRunStatus;
import org.example.paperaiagent.writing.section.SectionVersionStatus;
import org.example.paperaiagent.writing.section.WritingContextBuilder;
import org.example.paperaiagent.writing.citation.CitationOutputParser;
import org.example.paperaiagent.writing.citation.CitationValidator;
import org.example.paperaiagent.writing.citation.CitationValidationStatus;
import org.example.paperaiagent.writing.task.WritingStage;
import org.example.paperaiagent.writing.task.WritingTask;
import org.example.paperaiagent.writing.workflow.StageCapabilityPolicy;
import org.example.paperaiagent.writing.workflow.WritingWorkflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectionV1ContractTest {
    private final ObjectMapper json = new ObjectMapper();
    private InMemoryWritingTaskRepository tasks;
    private InMemoryTaskRunRepository runs;
    private InMemoryTaskEvidenceRepository evidence;
    private InMemoryOutlineVersionRepository outlines;
    private InMemoryOutlineSectionRepository outlineSections;
    private InMemorySectionVersionRepository sections;
    private InMemorySectionClaimRepository claims;
    private InMemoryClaimCitationRepository citations;
    private ScriptedRuntime runtime;
    private WritingTaskApplicationService taskService;
    private SectionWritingApplicationService sectionService;

    @BeforeEach
    void setUp() {
        tasks = new InMemoryWritingTaskRepository();
        runs = new InMemoryTaskRunRepository();
        evidence = new InMemoryTaskEvidenceRepository();
        outlines = new InMemoryOutlineVersionRepository();
        outlineSections = new InMemoryOutlineSectionRepository();
        sections = new InMemorySectionVersionRepository();
        claims = new InMemorySectionClaimRepository();
        citations = new InMemoryClaimCitationRepository();
        runtime = new ScriptedRuntime();
        WritingWorkflow workflow = new WritingWorkflow(new StageCapabilityPolicy());
        var transactions = new InMemoryWritingTransactionOperations();
        taskService = new WritingTaskApplicationService(tasks, runs, evidence, outlines,
                outlineSections, new OutlineStructureExtractor(), new OutlineContextSelector(20, 30000),
                workflow, new TaskEvidenceAdoptionService(json), runtime, transactions);
        sectionService = new SectionWritingApplicationService(tasks, runs, outlines, outlineSections,
                sections, claims, citations, evidence,
                new WritingContextBuilder(evidence, outlineSections, sections, 12, 20000, 8000),
                new CitationOutputParser(json), new CitationValidator(), workflow, runtime, transactions);
    }

    @Test
    void extractorCreatesStableTreeAndIgnoresCodeFence() {
        OutlineVersion outline = outline("""
                # Paper
                ```java
                ## not a section
                ```
                ## Introduction
                Explain the problem.
                ## Method
                ### Encoder
                ### Fusion
                """);
        var tree = new OutlineStructureExtractor().extract(outline);

        assertEquals(List.of("s1", "s1.1", "s1.2", "s1.2.1", "s1.2.2"),
                tree.stream().map(item -> item.sectionKey()).toList());
        assertFalse(tree.getFirst().writable());
        assertTrue(tree.get(1).writable());
        assertEquals("Explain the problem.", tree.get(1).objective());
    }

    @Test
    void extractorRejectsHeadingDepthJump() {
        OutlineVersion outline = outline("# Paper\n### Invalid jump");
        assertThrows(OutlineStructureInvalidException.class,
                () -> new OutlineStructureExtractor().extract(outline));
    }

    @Test
    void confirmationCreatesTreeAndRepeatedConfirmationIsIdempotent() {
        var task = prepareConfirmedTask();
        var first = sectionService.listOutlineSections(task.taskId());
        taskService.confirmOutline(task.taskId(), task.confirmedOutlineVersionId());
        var second = sectionService.listOutlineSections(task.taskId());

        assertEquals(5, first.size());
        assertEquals(first, second);
        assertEquals(List.of("s1", "s1.1", "s1.2", "s1.2.1", "s1.2.2"),
                first.stream().map(item -> item.sectionKey()).toList());
    }

    @Test
    void historicalConfirmedOutlineGetsIdempotentStructureBackfill() {
        WritingTask task = WritingTask.create("Historic", "Topic", "R");
        task.startResearch();
        task.markOutlinePending();
        OutlineVersion outline = outline(task.id(), "# Paper\n## Introduction\nGoal");
        outline.confirm();
        task.confirmOutline(outline.id());
        tasks.save(task);
        outlines.save(outline);

        assertFalse(outlineSections.existsByOutlineVersionId(outline.id()));
        taskService.confirmOutline(task.id().toString(), outline.id().toString());
        assertEquals(2, outlineSections.findByOutlineVersionId(outline.id()).size());
    }

    @Test
    void generatesImmutableVersionWithEmptyToolkitAndSupportsIdempotency() {
        var task = prepareConfirmedTask();
        var request = new GenerateSectionRequest("section-intro-1", "Write the introduction", null);
        var first = sectionService.generateSection(task.taskId(), "s1.1", request);
        var second = sectionService.generateSection(task.taskId(), "s1.1", request);

        assertEquals(first.sectionVersion().sectionVersionId(), second.sectionVersion().sectionVersionId());
        assertEquals(SectionVersionStatus.DRAFT, first.sectionVersion().status());
        assertEquals(2, first.sectionVersion().contextEvidenceIds().size());
        assertEquals(1, first.sectionVersion().citationSchemaVersion());
        assertEquals(CitationValidationStatus.STRUCTURE_VALIDATED,
                first.sectionVersion().citationValidationStatus());
        assertEquals(1, first.sectionVersion().claimCount());
        assertEquals(1, first.sectionVersion().citationCount());
        assertEquals(WritingStage.DRAFTING, taskService.getTask(task.taskId()).stage());
        AgentRunCommand sectionCommand = runtime.commands.stream()
                .filter(command -> "generate_section".equals(command.metadata().get("operation")))
                .findFirst().orElseThrow();
        assertEquals(java.util.Set.of(), sectionCommand.allowedTools());
        assertTrue(sectionCommand.message().contains("AHL evidence"));
        assertTrue(sectionCommand.message().contains("evidenceAlias=E1"));
        assertFalse(sectionCommand.message().contains(
                evidence.findByTaskId(org.example.paperaiagent.writing.task.WritingTaskId.from(task.taskId()))
                        .getFirst().id().toString()));
        assertEquals(1, runtime.commands.stream()
                .filter(command -> "generate_section".equals(command.metadata().get("operation"))).count());

        WritingConflictException conflict = assertThrows(WritingConflictException.class,
                () -> sectionService.generateSection(task.taskId(), "s1.1",
                        new GenerateSectionRequest("section-intro-1", "Different", null)));
        assertEquals("IDEMPOTENCY_CONFLICT", conflict.code());
    }

    @Test
    void rejectsDirectoryAndUnknownSection() {
        var task = prepareConfirmedTask();
        WritingConflictException directory = assertThrows(WritingConflictException.class,
                () -> sectionService.generateSection(task.taskId(), "s1",
                        new GenerateSectionRequest("directory", "write", null)));
        assertEquals("SECTION_NOT_WRITABLE", directory.code());
        assertThrows(org.example.paperaiagent.writing.application.OutlineSectionNotFoundException.class,
                () -> sectionService.generateSection(task.taskId(), "s9",
                        new GenerateSectionRequest("missing", "write", null)));
    }

    @Test
    void confirmedPreviousSectionEntersContextAndAllLeavesCompleteDraft() {
        var task = prepareConfirmedTask();
        var intro = sectionService.generateSection(task.taskId(), "s1.1",
                new GenerateSectionRequest("intro", "write", null));
        sectionService.confirmSection(task.taskId(), "s1.1", intro.sectionVersion().sectionVersionId());

        var encoder = sectionService.generateSection(task.taskId(), "s1.2.1",
                new GenerateSectionRequest("encoder", "write", null));
        AgentRunCommand encoderCommand = runtime.commands.getLast();
        assertTrue(encoderCommand.message().contains(intro.sectionVersion().contentSnapshot()));
        sectionService.confirmSection(task.taskId(), "s1.2.1", encoder.sectionVersion().sectionVersionId());

        var fusion = sectionService.generateSection(task.taskId(), "s1.2.2",
                new GenerateSectionRequest("fusion", "write", null));
        sectionService.confirmSection(task.taskId(), "s1.2.2", fusion.sectionVersion().sectionVersionId());

        assertEquals(WritingStage.DRAFT_COMPLETED, taskService.getTask(task.taskId()).stage());
    }

    @Test
    void onlyOneVersionCanBeConfirmedForASection() {
        var task = prepareConfirmedTask();
        var first = sectionService.generateSection(task.taskId(), "s1.1",
                new GenerateSectionRequest("intro-v1", "v1", null));
        var second = sectionService.generateSection(task.taskId(), "s1.1",
                new GenerateSectionRequest("intro-v2", "v2", null));
        sectionService.confirmSection(task.taskId(), "s1.1", first.sectionVersion().sectionVersionId());

        WritingConflictException conflict = assertThrows(WritingConflictException.class,
                () -> sectionService.confirmSection(task.taskId(), "s1.1",
                        second.sectionVersion().sectionVersionId()));
        assertEquals("SECTION_ALREADY_CONFIRMED", conflict.code());
    }

    @Test
    void legacyDraftCannotBeConfirmedButKeepsSchemaZero() {
        var taskResponse = prepareConfirmedTask();
        WritingTask task = tasks.findById(org.example.paperaiagent.writing.task.WritingTaskId
                .from(taskResponse.taskId())).orElseThrow();
        task.startDrafting();
        tasks.save(task);
        var target = outlineSections.findByOutlineVersionIdAndSectionKey(
                task.confirmedOutlineVersionId(), "s1.1").orElseThrow();
        var evidenceId = evidence.findByTaskId(task.id()).getFirst().id();
        var legacy = org.example.paperaiagent.writing.section.SectionVersion.draft(
                task.id(), task.confirmedOutlineVersionId(), target.id(), TaskRunId.newId(), 1,
                target.title(), "Legacy body", WritingHashes.sha256("Legacy body"),
                List.of(new org.example.paperaiagent.writing.section.SectionEvidenceReference(evidenceId, 1)));
        sections.save(legacy);

        assertEquals(0, legacy.citationSchemaVersion());
        assertEquals(CitationValidationStatus.LEGACY_UNVALIDATED, legacy.citationValidationStatus());
        WritingConflictException conflict = assertThrows(WritingConflictException.class,
                () -> sectionService.confirmSection(task.id().toString(), "s1.1", legacy.id().toString()));
        assertEquals("CITATION_VALIDATION_REQUIRED", conflict.code());
        IllegalStateException domainConflict = assertThrows(IllegalStateException.class, legacy::confirm);
        assertEquals("CITATION_VALIDATION_REQUIRED", domainConflict.getMessage());
    }

    @Test
    void unconfirmedOutlineAndMissingEvidenceRejectSectionGeneration() {
        var created = taskService.createTask(new CreateWritingTaskRequest("Unconfirmed", "Topic", "R"));
        IllegalStateException unconfirmed = assertThrows(IllegalStateException.class,
                () -> sectionService.generateSection(created.taskId(), "s1",
                        new GenerateSectionRequest("unconfirmed", "write", null)));
        assertTrue(unconfirmed.getMessage().contains("CREATED"));

        WritingTask task = WritingTask.create("No evidence", "Topic", "R");
        task.startResearch();
        task.markOutlinePending();
        OutlineVersion outline = outline(task.id(), "# Paper\n## Introduction\nGoal");
        outlines.save(outline);
        outlineSections.saveAll(new OutlineStructureExtractor().extract(outline));
        outline.confirm();
        task.confirmOutline(outline.id());
        tasks.save(task);

        WritingConflictException noEvidence = assertThrows(WritingConflictException.class,
                () -> sectionService.generateSection(task.id().toString(), "s1.1",
                        new GenerateSectionRequest("no-evidence", "write", null)));
        assertEquals("SECTION_EVIDENCE_REQUIRED", noEvidence.code());
    }

    @Test
    void writingContextAppliesDeterministicEvidenceAndPreviousSectionBudgets() {
        var taskResponse = prepareConfirmedTask();
        var intro = sectionService.generateSection(taskResponse.taskId(), "s1.1",
                new GenerateSectionRequest("budget-intro", "write", null));
        sectionService.confirmSection(taskResponse.taskId(), "s1.1", intro.sectionVersion().sectionVersionId());

        WritingTask task = tasks.findById(org.example.paperaiagent.writing.task.WritingTaskId
                .from(taskResponse.taskId())).orElseThrow();
        OutlineVersion outline = outlines.findById(task.confirmedOutlineVersionId()).orElseThrow();
        var target = outlineSections.findByOutlineVersionIdAndSectionKey(outline.id(), "s1.2.1").orElseThrow();
        var limited = new WritingContextBuilder(evidence, outlineSections, sections, 1, 60, 50)
                .build(task, outline, target);

        assertEquals(1, limited.evidence().size());
        assertTrue(limited.evidenceCharacterCount() <= 60);
        assertTrue(limited.evidence().getFirst().renderedText().contains("[Evidence content truncated]"));
        assertEquals(1, limited.previousSections().size());
        assertTrue(limited.previousSectionCharacterCount() <= 50);
        assertTrue(limited.previousSections().getFirst().renderedText()
                .contains("[Previous section content truncated]"));
    }

    @Test
    void runtimeFailureMarksRunFailedAndDoesNotAdvanceTask() {
        var task = prepareConfirmedTask();
        runtime.failSectionRuns = true;

        var response = sectionService.generateSection(task.taskId(), "s1.1",
                new GenerateSectionRequest("failed-section", "write", null));

        assertEquals(TaskRunStatus.FAILED, response.run().status());
        assertEquals("RUNTIME_ERROR", response.run().errorCode());
        assertEquals(null, response.sectionVersion());
        assertEquals(WritingStage.OUTLINE_CONFIRMED, taskService.getTask(task.taskId()).stage());
        assertTrue(sectionService.listSections(task.taskId()).isEmpty());
    }

    @Test
    void anyRunningActionSerializesAllAgentWorkForTheTask() {
        var task = prepareConfirmedTask();
        var taskId = org.example.paperaiagent.writing.task.WritingTaskId.from(task.taskId());
        TaskRun blocker = TaskRun.create(taskId, TaskRunAction.RESEARCH, "blocking-research",
                WritingHashes.sha256("blocking"), "blocking-session", "blocking input");
        blocker.start();
        runs.save(blocker);

        WritingConflictException conflict = assertThrows(WritingConflictException.class,
                () -> sectionService.generateSection(task.taskId(), "s1.1",
                        new GenerateSectionRequest("blocked-section", "write", null)));
        assertEquals("TASK_RUN_CONFLICT", conflict.code());
    }

    private org.example.paperaiagent.writing.application.dto.WritingTaskResponse prepareConfirmedTask() {
        var task = taskService.createTask(new CreateWritingTaskRequest("ALMT", "ALMT", "Use evidence"));
        taskService.startResearch(task.taskId(), new ResearchRequest("research", "research-1", null));
        var generated = taskService.generateOutline(task.taskId(),
                new GenerateOutlineRequest("outline-1", "outline", null));
        taskService.confirmOutline(task.taskId(), generated.outline().outlineVersionId());
        return taskService.getTask(task.taskId());
    }

    private OutlineVersion outline(String markdown) {
        return outline(WritingTask.create("T", "T", "").id(), markdown);
    }

    private OutlineVersion outline(org.example.paperaiagent.writing.task.WritingTaskId taskId, String markdown) {
        return OutlineVersion.draft(taskId, TaskRunId.newId(), 1,
                "Outline", markdown, WritingHashes.sha256(markdown),
                List.of(new OutlineEvidenceReference(TaskEvidenceId.newId(), 1)));
    }

    private final class ScriptedRuntime implements PaperAgentRuntime {
        private final List<AgentRunCommand> commands = new CopyOnWriteArrayList<>();
        private boolean failSectionRuns;

        @Override
        public Flux<AgentRuntimeEvent> stream(AgentRunCommand command) {
            commands.add(command);
            String operation = String.valueOf(command.metadata().get("operation"));
            if ("research".equals(operation)) {
                KnowledgeSearchResult result = KnowledgeSearchResult.of("ALMT", 2, List.of(
                        new EvidenceCandidate("chunk-1", "doc", "（4）ALMT", "3 Method",
                                List.of(2, 3), "AHL evidence", 0.95),
                        new EvidenceCandidate("chunk-2", "doc", "（4）ALMT", "4 Experiments",
                                List.of(5, 6), "Experiment evidence", 0.90)));
                return Flux.just(toolCompleted(result), completed("research complete"));
            }
            if ("generate_outline".equals(operation)) {
                return Flux.just(completed("""
                        # ALMT Paper
                        ## Introduction
                        Explain the problem and contribution.
                        ## Method
                        ### Encoder
                        Explain feature encoding.
                        ### Fusion
                        Explain adaptive fusion.
                        """));
            }
            if (failSectionRuns) {
                return Flux.just(new AgentRuntimeEvent(2, Instant.now(), AgentRuntimeEvent.Type.RUN_FAILED,
                        null, null, Map.of("errorCode", "SCRIPTED_FAILURE", "message", "section failed")));
            }
            return Flux.just(completed("""
                    {
                      "bodyMarkdown": "AHL evidence supports this section.[C1]",
                      "claims": [
                        {
                          "claimKey": "C1",
                          "claimText": "AHL evidence supports this section.",
                          "citations": [
                            {"evidenceAlias": "E1", "supportingQuote": "AHL evidence"}
                          ]
                        }
                      ]
                    }
                    """));
        }
    }

    private AgentRuntimeEvent toolCompleted(Object structured) {
        return new AgentRuntimeEvent(1, Instant.now(), AgentRuntimeEvent.Type.TOOL_COMPLETED,
                PaperToolNames.SEARCH_KNOWLEDGE, "call-1",
                Map.of("structuredResult", json.convertValue(structured, Object.class), "rawResult", "fixture"));
    }

    private static AgentRuntimeEvent completed(String answer) {
        return new AgentRuntimeEvent(2, Instant.now(), AgentRuntimeEvent.Type.RUN_COMPLETED,
                null, null, Map.of("finalAnswer", answer));
    }
}
