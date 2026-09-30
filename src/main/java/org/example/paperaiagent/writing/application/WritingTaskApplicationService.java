package org.example.paperaiagent.writing.application;

import org.example.paperaiagent.agent.runtime.AgentRunCommand;
import org.example.paperaiagent.agent.runtime.AgentRunResult;
import org.example.paperaiagent.agent.runtime.PaperAgentRuntime;
import org.example.paperaiagent.writing.application.dto.CreateWritingTaskRequest;
import org.example.paperaiagent.writing.application.dto.GenerateOutlineRequest;
import org.example.paperaiagent.writing.application.dto.OutlineGenerationResponse;
import org.example.paperaiagent.writing.application.dto.OutlineVersionResponse;
import org.example.paperaiagent.writing.application.dto.ResearchRequest;
import org.example.paperaiagent.writing.application.dto.TaskEvidenceResponse;
import org.example.paperaiagent.writing.application.dto.TaskRunResponse;
import org.example.paperaiagent.writing.application.dto.WritingTaskResponse;
import org.example.paperaiagent.writing.evidence.EvidenceAdoptionPlan;
import org.example.paperaiagent.writing.evidence.TaskEvidence;
import org.example.paperaiagent.writing.evidence.TaskEvidenceAdoptionService;
import org.example.paperaiagent.writing.evidence.TaskEvidenceRepository;
import org.example.paperaiagent.writing.run.TaskRun;
import org.example.paperaiagent.writing.run.TaskRunAction;
import org.example.paperaiagent.writing.run.TaskRunId;
import org.example.paperaiagent.writing.run.TaskRunRepository;
import org.example.paperaiagent.writing.task.WritingTask;
import org.example.paperaiagent.writing.task.WritingTaskId;
import org.example.paperaiagent.writing.task.WritingTaskRepository;
import org.example.paperaiagent.writing.outline.OutlineContext;
import org.example.paperaiagent.writing.outline.OutlineContextSelector;
import org.example.paperaiagent.writing.outline.OutlineEvidenceReference;
import org.example.paperaiagent.writing.outline.OutlineStatus;
import org.example.paperaiagent.writing.outline.OutlineVersion;
import org.example.paperaiagent.writing.outline.OutlineVersionId;
import org.example.paperaiagent.writing.outline.OutlineVersionRepository;
import org.example.paperaiagent.writing.outline.OutlineSectionRepository;
import org.example.paperaiagent.writing.outline.OutlineStructureExtractor;
import org.example.paperaiagent.writing.outline.OutlineStructureInvalidException;
import org.example.paperaiagent.writing.workflow.WritingWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class WritingTaskApplicationService {

    private static final Logger log = LoggerFactory.getLogger(WritingTaskApplicationService.class);

    private final WritingTaskRepository taskRepository;
    private final TaskRunRepository runRepository;
    private final TaskEvidenceRepository evidenceRepository;
    private final OutlineVersionRepository outlineRepository;
    private final OutlineSectionRepository outlineSectionRepository;
    private final OutlineStructureExtractor outlineStructureExtractor;
    private final OutlineContextSelector outlineContextSelector;
    private final WritingWorkflow workflow;
    private final TaskEvidenceAdoptionService evidenceAdoptionService;
    private final PaperAgentRuntime agentRuntime;
    private final WritingTransactionOperations transactions;
    private final ConcurrentMap<WritingTaskId, Object> taskLocks = new ConcurrentHashMap<>();

    public WritingTaskApplicationService(
            WritingTaskRepository taskRepository,
            TaskRunRepository runRepository,
            TaskEvidenceRepository evidenceRepository,
            OutlineVersionRepository outlineRepository,
            OutlineSectionRepository outlineSectionRepository,
            OutlineStructureExtractor outlineStructureExtractor,
            OutlineContextSelector outlineContextSelector,
            WritingWorkflow workflow,
            TaskEvidenceAdoptionService evidenceAdoptionService,
            PaperAgentRuntime agentRuntime,
            WritingTransactionOperations transactions
    ) {
        this.taskRepository = taskRepository;
        this.runRepository = runRepository;
        this.evidenceRepository = evidenceRepository;
        this.outlineRepository = outlineRepository;
        this.outlineSectionRepository = outlineSectionRepository;
        this.outlineStructureExtractor = outlineStructureExtractor;
        this.outlineContextSelector = outlineContextSelector;
        this.workflow = workflow;
        this.evidenceAdoptionService = evidenceAdoptionService;
        this.agentRuntime = agentRuntime;
        this.transactions = transactions;
    }

    public WritingTaskResponse createTask(CreateWritingTaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        WritingTask task = WritingTask.create(request.title(), request.topic(), request.requirements());
        return WritingTaskResponse.from(transactions.required(() -> taskRepository.save(task)));
    }

    public TaskRunResponse startResearch(String taskIdValue, ResearchRequest request) {
        WritingTaskId taskId = WritingTaskId.from(taskIdValue);
        ResearchRequest safeRequest = request == null ? new ResearchRequest(null, null, null) : request;
        String requestId = requireRequestId(safeRequest.requestId());

        ResearchAcceptance acceptance = acceptResearch(taskId, safeRequest, requestId);
        if (acceptance.idempotentRun() != null) {
            return TaskRunResponse.from(acceptance.idempotentRun());
        }

        TaskRun run = acceptance.run();
        AgentRunResult result;
        try {
            result = agentRuntime.run(new AgentRunCommand(
                    run.inputSnapshot(),
                    run.runtimeSessionId(),
                    run.id().toString(),
                    acceptance.allowedTools(),
                    Map.of(
                            "taskId", taskId.toString(),
                            "requestId", requestId,
                            "operation", "research"
                    )
            )).block();
            if (result == null) {
                throw new IllegalStateException("Agent runtime returned no result");
            }
        } catch (Exception exception) {
            run = failRun(run, "RUNTIME_ERROR", safeMessage(exception));
            return TaskRunResponse.from(run);
        }

        if (result.status() == AgentRunResult.Status.FAILED) {
            String errorCode = "TOOL_FAILED".equals(result.errorCode()) ? "TOOL_ERROR" : "RUNTIME_ERROR";
            run = failRun(run, errorCode, result.errorMessage());
            return TaskRunResponse.from(run);
        }

        try {
            EvidenceAdoptionPlan plan = evidenceAdoptionService.plan(taskId, run.id(), result, evidenceRepository);
            TaskRun currentRun = run;
            run = transactions.required(() -> {
                List<TaskEvidence> inserted = evidenceRepository.saveAll(plan.evidence());
                TaskRun persisted = runRepository.findById(currentRun.id()).orElse(currentRun);
                persisted.succeed(result.finalAnswer(), inserted.size());
                return runRepository.save(persisted);
            });
        } catch (Exception exception) {
            run = failRun(run, "RESULT_PERSISTENCE_ERROR", safeMessage(exception));
        }
        return TaskRunResponse.from(run);
    }

    public WritingTaskResponse getTask(String taskIdValue) {
        WritingTaskId taskId = WritingTaskId.from(taskIdValue);
        return WritingTaskResponse.from(findTask(taskId));
    }

    public TaskRunResponse getRun(String runIdValue) {
        TaskRunId runId = TaskRunId.from(runIdValue);
        return runRepository.findById(runId)
                .map(TaskRunResponse::from)
                .orElseThrow(() -> new TaskRunNotFoundException(runIdValue));
    }

    public List<TaskEvidenceResponse> listEvidence(String taskIdValue) {
        WritingTaskId taskId = WritingTaskId.from(taskIdValue);
        findTask(taskId);
        return evidenceRepository.findByTaskId(taskId).stream()
                .map(TaskEvidenceResponse::from)
                .toList();
    }

    public OutlineGenerationResponse generateOutline(String taskIdValue, GenerateOutlineRequest request) {
        WritingTaskId taskId = WritingTaskId.from(taskIdValue);
        if (request == null) throw new IllegalArgumentException("request must not be null");
        String requestId = requireRequestId(request.requestId());
        OutlineAcceptance acceptance = acceptOutline(taskId, request, requestId);
        if (acceptance.idempotentRun() != null) {
            OutlineVersion existing = outlineRepository.findByGeneratedRunId(acceptance.idempotentRun().id()).orElse(null);
            return new OutlineGenerationResponse(TaskRunResponse.from(acceptance.idempotentRun()),
                    existing == null ? null : OutlineVersionResponse.from(existing));
        }

        TaskRun run = acceptance.run();
        AgentRunResult result;
        try {
            result = agentRuntime.run(new AgentRunCommand(
                    run.inputSnapshot(), run.runtimeSessionId(), run.id().toString(), Set.of(),
                    Map.of("taskId", taskId.toString(), "requestId", requestId, "operation", "generate_outline")
            )).block();
            if (result == null) throw new IllegalStateException("Agent runtime returned no result");
        } catch (Exception exception) {
            TaskRun failed = failRun(run, "RUNTIME_ERROR", safeMessage(exception));
            return new OutlineGenerationResponse(TaskRunResponse.from(failed), null);
        }
        if (result.status() == AgentRunResult.Status.FAILED) {
            TaskRun failed = failRun(run, "RUNTIME_ERROR", result.errorMessage());
            return new OutlineGenerationResponse(TaskRunResponse.from(failed), null);
        }

        try {
            String answer = result.finalAnswer();
            OutlineCompletion completed = transactions.required(() -> {
                TaskRun persistedRun = runRepository.findById(run.id()).orElseThrow();
                if (persistedRun.status() != org.example.paperaiagent.writing.run.TaskRunStatus.RUNNING) {
                    throw new IllegalStateException("Outline run is no longer RUNNING");
                }
                int versionNumber = outlineRepository.nextVersionNumber(taskId);
                List<OutlineEvidenceReference> references = java.util.stream.IntStream
                        .range(0, acceptance.context().evidence().size())
                        .mapToObj(index -> new OutlineEvidenceReference(
                                acceptance.context().evidence().get(index).id(), index + 1))
                        .toList();
                OutlineVersion outline = OutlineVersion.draft(
                        taskId, persistedRun.id(), versionNumber,
                        acceptance.taskTitle() + " 提纲 v" + versionNumber,
                        answer, WritingHashes.sha256(answer), references);
                outlineRepository.save(outline);
                persistedRun.succeed(answer, 0);
                runRepository.save(persistedRun);
                WritingTask persistedTask = findTask(taskId);
                persistedTask.markOutlinePending();
                taskRepository.save(persistedTask);
                return new OutlineCompletion(persistedRun, outline);
            });
            return new OutlineGenerationResponse(
                    TaskRunResponse.from(completed.run()), OutlineVersionResponse.from(completed.outline()));
        } catch (Exception exception) {
            TaskRun failed = failRun(run, "RESULT_PERSISTENCE_ERROR", safeMessage(exception));
            return new OutlineGenerationResponse(TaskRunResponse.from(failed), null);
        }
    }

    public List<OutlineVersionResponse> listOutlineVersions(String taskIdValue) {
        WritingTaskId taskId = WritingTaskId.from(taskIdValue);
        findTask(taskId);
        return outlineRepository.findByTaskId(taskId).stream().map(OutlineVersionResponse::from).toList();
    }

    public OutlineVersionResponse getOutlineVersion(String outlineVersionIdValue) {
        OutlineVersionId id = OutlineVersionId.from(outlineVersionIdValue);
        return OutlineVersionResponse.from(outlineRepository.findById(id)
                .orElseThrow(() -> new OutlineNotFoundException(outlineVersionIdValue)));
    }

    public OutlineVersionResponse confirmOutline(String taskIdValue, String outlineVersionIdValue) {
        WritingTaskId taskId = WritingTaskId.from(taskIdValue);
        OutlineVersionId outlineId = OutlineVersionId.from(outlineVersionIdValue);
        Object lock = taskLocks.computeIfAbsent(taskId, ignored -> new Object());
        synchronized (lock) {
            return transactions.required(() -> {
                WritingTask task = findTask(taskId);
                OutlineVersion outline = outlineRepository.findById(outlineId)
                        .orElseThrow(() -> new OutlineNotFoundException(outlineVersionIdValue));
                if (!outline.taskId().equals(taskId)) {
                    throw new WritingConflictException("OUTLINE_TASK_MISMATCH", "Outline does not belong to task");
                }
                if (task.stage() == org.example.paperaiagent.writing.task.WritingStage.OUTLINE_CONFIRMED) {
                    if (outlineId.equals(task.confirmedOutlineVersionId())
                            && outline.status() == OutlineStatus.CONFIRMED) {
                        ensureOutlineSections(outline);
                        return OutlineVersionResponse.from(outline);
                    }
                    throw new WritingConflictException("OUTLINE_ALREADY_CONFIRMED",
                            "The task already has a confirmed outline");
                }
                ensureOutlineSections(outline);
                outline.confirm();
                outlineRepository.save(outline);
                task.confirmOutline(outlineId);
                taskRepository.save(task);
                return OutlineVersionResponse.from(outline);
            });
        }
    }

    private ResearchAcceptance acceptResearch(
            WritingTaskId taskId,
            ResearchRequest request,
            String requestId
    ) {
        Object lock = taskLocks.computeIfAbsent(taskId, ignored -> new Object());
        synchronized (lock) {
            return transactions.required(() -> {
                WritingTask task = findTask(taskId);
                String prompt = researchPrompt(task, request.message());
                String requestHash = WritingHashes.sha256(TaskRunAction.RESEARCH.name() + "\n" + prompt);
                TaskRun existing = runRepository.findByTaskIdActionAndRequestId(
                        taskId, TaskRunAction.RESEARCH, requestId).orElse(null);
                if (existing != null) {
                    requireMatchingHash(existing, requestHash);
                    return ResearchAcceptance.idempotent(existing);
                }
                if (runRepository.existsRunningByTaskId(taskId)) {
                    throw new ResearchRunConflictException(
                            "Writing task " + taskId + " already has a running research run"
                    );
                }

                Set<String> allowedTools = workflow.startResearch(task);
                taskRepository.save(task);

                String sessionId = normalizeOrDefault(request.sessionId(), taskId.toString());
                TaskRun run = TaskRun.create(taskId, TaskRunAction.RESEARCH, requestId, requestHash,
                        sessionId, prompt);
                run.start();
                runRepository.save(run);
                return ResearchAcceptance.started(run, allowedTools);
            });
        }
    }

    private OutlineAcceptance acceptOutline(
            WritingTaskId taskId, GenerateOutlineRequest request, String requestId
    ) {
        Object lock = taskLocks.computeIfAbsent(taskId, ignored -> new Object());
        synchronized (lock) {
            return transactions.required(() -> {
                WritingTask task = findTask(taskId);
                workflow.requireOutlineGenerationAllowed(task);
                OutlineContext context = outlineContextSelector.select(evidenceRepository.findByTaskId(taskId));
                if (context.evidence().isEmpty()) {
                    throw new WritingConflictException("OUTLINE_EVIDENCE_REQUIRED",
                            "At least one adopted evidence item is required to generate an outline");
                }
                String instruction = normalizeOrDefault(request.instruction(),
                        "生成结构清晰、能够映射到已有证据的论文提纲");
                String requestHash = WritingHashes.sha256(TaskRunAction.GENERATE_OUTLINE.name() + "\n" + instruction);
                TaskRun existing = runRepository.findByTaskIdActionAndRequestId(
                        taskId, TaskRunAction.GENERATE_OUTLINE, requestId).orElse(null);
                if (existing != null) {
                    requireMatchingHash(existing, requestHash);
                    return OutlineAcceptance.idempotent(existing);
                }
                if (runRepository.existsRunningByTaskId(taskId)) {
                    throw new WritingConflictException("OUTLINE_RUN_CONFLICT",
                            "The task already has a running outline generation");
                }
                String prompt = outlinePrompt(task, context, instruction);
                String sessionId = normalizeOrDefault(request.sessionId(), taskId + "-outline");
                TaskRun run = TaskRun.create(taskId, TaskRunAction.GENERATE_OUTLINE, requestId,
                        requestHash, sessionId, prompt);
                run.start();
                runRepository.save(run);
                return OutlineAcceptance.started(run, context, task.title());
            });
        }
    }

    private TaskRun failRun(TaskRun run, String errorCode, String errorSummary) {
        try {
            return transactions.required(() -> {
                TaskRun persisted = runRepository.findById(run.id()).orElse(run);
                persisted.fail(errorCode, errorSummary);
                return runRepository.save(persisted);
            });
        } catch (Exception persistenceFailure) {
            log.error("Failed to persist terminal state for writing run {}", run.id(), persistenceFailure);
            return run;
        }
    }

    private void ensureOutlineSections(OutlineVersion outline) {
        if (outlineSectionRepository.existsByOutlineVersionId(outline.id())) return;
        try {
            outlineSectionRepository.saveAll(outlineStructureExtractor.extract(outline));
        } catch (OutlineStructureInvalidException exception) {
            throw new WritingConflictException("OUTLINE_STRUCTURE_INVALID", exception.getMessage());
        }
    }

    private WritingTask findTask(WritingTaskId taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new WritingTaskNotFoundException(taskId.toString()));
    }

    private static String researchPrompt(WritingTask task, String requestedMessage) {
        if (requestedMessage != null && !requestedMessage.isBlank()) {
            return requestedMessage.trim();
        }
        return "Research the topic '" + task.topic() + "'. Requirements: "
                + (task.requirements().isBlank() ? "provide a source-grounded research summary" : task.requirements());
    }

    private static String requireRequestId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        return value.trim();
    }

    private static String normalizeOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private static void requireMatchingHash(TaskRun run, String requestHash) {
        if (!run.requestHash().equals(requestHash)) {
            throw new WritingConflictException("IDEMPOTENCY_CONFLICT",
                    "The idempotency key was already used with different request content");
        }
    }

    private static String outlinePrompt(WritingTask task, OutlineContext context, String instruction) {
        return """
                你正在为一个论文写作任务生成提纲。只能使用下列已采纳证据，不得调用工具或补造来源。
                任务主题：%s
                写作要求：%s
                本次要求：%s

                已采纳证据：%s

                请输出 Markdown 提纲。每个主要章节说明其目标，并标注支持它的 evidenceId。
                """.formatted(task.topic(), task.requirements(), instruction, context.renderedEvidence());
    }

    private record ResearchAcceptance(TaskRun run, Set<String> allowedTools, TaskRun idempotentRun) {

        private static ResearchAcceptance started(TaskRun run, Set<String> allowedTools) {
            return new ResearchAcceptance(run, Set.copyOf(allowedTools), null);
        }

        private static ResearchAcceptance idempotent(TaskRun run) {
            return new ResearchAcceptance(null, Set.of(), run);
        }
    }

    private record OutlineAcceptance(
            TaskRun run, OutlineContext context, String taskTitle, TaskRun idempotentRun
    ) {
        private static OutlineAcceptance started(TaskRun run, OutlineContext context, String taskTitle) {
            return new OutlineAcceptance(run, context, taskTitle, null);
        }
        private static OutlineAcceptance idempotent(TaskRun run) {
            return new OutlineAcceptance(null, null, null, run);
        }
    }

    private record OutlineCompletion(TaskRun run, OutlineVersion outline) { }
}
