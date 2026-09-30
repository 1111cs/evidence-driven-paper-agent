package org.example.paperaiagent.writing.application;

import org.example.paperaiagent.agent.runtime.AgentRunCommand;
import org.example.paperaiagent.agent.runtime.AgentRunResult;
import org.example.paperaiagent.agent.runtime.PaperAgentRuntime;
import org.example.paperaiagent.writing.application.dto.ClaimCitationResponse;
import org.example.paperaiagent.writing.application.dto.GenerateSectionRequest;
import org.example.paperaiagent.writing.application.dto.OutlineSectionResponse;
import org.example.paperaiagent.writing.application.dto.SectionGenerationResponse;
import org.example.paperaiagent.writing.application.dto.SectionVersionResponse;
import org.example.paperaiagent.writing.application.dto.SectionClaimResponse;
import org.example.paperaiagent.writing.application.dto.TaskRunResponse;
import org.example.paperaiagent.writing.citation.CitationOutput;
import org.example.paperaiagent.writing.citation.CitationOutputParser;
import org.example.paperaiagent.writing.citation.CitationValidationException;
import org.example.paperaiagent.writing.citation.CitationValidationStatus;
import org.example.paperaiagent.writing.citation.CitationValidator;
import org.example.paperaiagent.writing.citation.ClaimCitation;
import org.example.paperaiagent.writing.citation.ClaimCitationRepository;
import org.example.paperaiagent.writing.citation.SectionClaim;
import org.example.paperaiagent.writing.citation.SectionClaimRepository;
import org.example.paperaiagent.writing.citation.ValidatedCitationOutput;
import org.example.paperaiagent.writing.evidence.TaskEvidence;
import org.example.paperaiagent.writing.evidence.TaskEvidenceId;
import org.example.paperaiagent.writing.evidence.TaskEvidenceRepository;
import org.example.paperaiagent.writing.outline.OutlineSection;
import org.example.paperaiagent.writing.outline.OutlineSectionRepository;
import org.example.paperaiagent.writing.outline.OutlineVersion;
import org.example.paperaiagent.writing.outline.OutlineVersionRepository;
import org.example.paperaiagent.writing.run.TaskRun;
import org.example.paperaiagent.writing.run.TaskRunAction;
import org.example.paperaiagent.writing.run.TaskRunRepository;
import org.example.paperaiagent.writing.run.TaskRunStatus;
import org.example.paperaiagent.writing.section.EvidenceContext;
import org.example.paperaiagent.writing.section.PreviousSectionContext;
import org.example.paperaiagent.writing.section.SectionEvidenceReference;
import org.example.paperaiagent.writing.section.SectionVersion;
import org.example.paperaiagent.writing.section.SectionVersionId;
import org.example.paperaiagent.writing.section.SectionVersionRepository;
import org.example.paperaiagent.writing.section.SectionVersionStatus;
import org.example.paperaiagent.writing.section.WritingContext;
import org.example.paperaiagent.writing.section.WritingContextBuilder;
import org.example.paperaiagent.writing.task.WritingStage;
import org.example.paperaiagent.writing.task.WritingTask;
import org.example.paperaiagent.writing.task.WritingTaskId;
import org.example.paperaiagent.writing.task.WritingTaskRepository;
import org.example.paperaiagent.writing.workflow.WritingWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
public class SectionWritingApplicationService {
    private static final Logger log = LoggerFactory.getLogger(SectionWritingApplicationService.class);
    private static final Pattern DATABASE_UUID = Pattern.compile(
            "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b");

    private final WritingTaskRepository taskRepository;
    private final TaskRunRepository runRepository;
    private final OutlineVersionRepository outlineRepository;
    private final OutlineSectionRepository outlineSectionRepository;
    private final SectionVersionRepository sectionVersionRepository;
    private final SectionClaimRepository sectionClaimRepository;
    private final ClaimCitationRepository claimCitationRepository;
    private final TaskEvidenceRepository evidenceRepository;
    private final WritingContextBuilder contextBuilder;
    private final CitationOutputParser citationOutputParser;
    private final CitationValidator citationValidator;
    private final WritingWorkflow workflow;
    private final PaperAgentRuntime agentRuntime;
    private final WritingTransactionOperations transactions;
    private final ConcurrentMap<WritingTaskId, Object> taskLocks = new ConcurrentHashMap<>();

    public SectionWritingApplicationService(
            WritingTaskRepository taskRepository,
            TaskRunRepository runRepository,
            OutlineVersionRepository outlineRepository,
            OutlineSectionRepository outlineSectionRepository,
            SectionVersionRepository sectionVersionRepository,
            SectionClaimRepository sectionClaimRepository,
            ClaimCitationRepository claimCitationRepository,
            TaskEvidenceRepository evidenceRepository,
            WritingContextBuilder contextBuilder,
            CitationOutputParser citationOutputParser,
            CitationValidator citationValidator,
            WritingWorkflow workflow,
            PaperAgentRuntime agentRuntime,
            WritingTransactionOperations transactions) {
        this.taskRepository = taskRepository;
        this.runRepository = runRepository;
        this.outlineRepository = outlineRepository;
        this.outlineSectionRepository = outlineSectionRepository;
        this.sectionVersionRepository = sectionVersionRepository;
        this.sectionClaimRepository = sectionClaimRepository;
        this.claimCitationRepository = claimCitationRepository;
        this.evidenceRepository = evidenceRepository;
        this.contextBuilder = contextBuilder;
        this.citationOutputParser = citationOutputParser;
        this.citationValidator = citationValidator;
        this.workflow = workflow;
        this.agentRuntime = agentRuntime;
        this.transactions = transactions;
    }

    public List<OutlineSectionResponse> listOutlineSections(String taskIdValue) {
        WritingTask task = findTask(WritingTaskId.from(taskIdValue));
        if (task.confirmedOutlineVersionId() == null) return List.of();
        return outlineSectionRepository.findByOutlineVersionId(task.confirmedOutlineVersionId()).stream()
                .map(OutlineSectionResponse::from).toList();
    }

    public SectionGenerationResponse generateSection(
            String taskIdValue, String sectionKey, GenerateSectionRequest request) {
        if (request == null) throw new IllegalArgumentException("request must not be null");
        WritingTaskId taskId = WritingTaskId.from(taskIdValue);
        String requestId = requireText(request.requestId(), "requestId");
        SectionAcceptance acceptance = accept(taskId, sectionKey, request, requestId);
        if (acceptance.idempotentRun() != null) {
            SectionVersion existing = sectionVersionRepository.findByGeneratedRunId(acceptance.idempotentRun().id())
                    .orElse(null);
            return response(acceptance.idempotentRun(), existing);
        }

        TaskRun run = acceptance.run();
        AgentRunResult result;
        try {
            result = agentRuntime.run(new AgentRunCommand(run.inputSnapshot(), run.runtimeSessionId(),
                    run.id().toString(), Set.of(), Map.of(
                    "taskId", taskId.toString(), "sectionKey", acceptance.section().sectionKey(),
                    "requestId", requestId, "operation", "generate_section"))).block();
            if (result == null) throw new IllegalStateException("Agent runtime returned no result");
        } catch (Exception exception) {
            return response(failRun(run, "RUNTIME_ERROR", safeMessage(exception)), null);
        }
        if (result.status() == AgentRunResult.Status.FAILED) {
            return response(failRun(run, "RUNTIME_ERROR", result.errorMessage()), null);
        }

        ValidatedCitationOutput validatedOutput;
        try {
            CitationOutput parsedOutput = citationOutputParser.parse(result.finalAnswer());
            validatedOutput = citationValidator.validate(parsedOutput, acceptance.context(), acceptance.section());
        } catch (CitationValidationException exception) {
            return response(failRun(run, exception.code(), safeMessage(exception)), null);
        } catch (Exception exception) {
            return response(failRun(run, "CITATION_OUTPUT_INVALID", safeMessage(exception)), null);
        }

        try {
            SectionCompletion completion = transactions.required(() -> {
                TaskRun persistedRun = runRepository.findById(run.id()).orElseThrow();
                if (persistedRun.status() != TaskRunStatus.RUNNING) {
                    throw new IllegalStateException("Section run is no longer RUNNING");
                }
                int versionNumber = sectionVersionRepository.nextVersionNumber(acceptance.section().id());
                List<SectionEvidenceReference> references = acceptance.context().evidence().stream()
                        .map(item -> new SectionEvidenceReference(item.evidenceId(), item.sequenceNumber()))
                        .toList();
                SectionVersion version = SectionVersion.citationValidatedDraft(taskId, acceptance.outline().id(),
                        acceptance.section().id(), persistedRun.id(), versionNumber,
                        acceptance.section().title(), validatedOutput.bodyMarkdown(),
                        WritingHashes.sha256(validatedOutput.bodyMarkdown()), references);
                sectionVersionRepository.save(version);

                List<SectionClaim> claims = validatedOutput.claims().stream()
                        .map(item -> SectionClaim.validated(taskId, version.id(), item.claimKey(),
                                item.claimText(), item.startOffset(), item.endOffset()))
                        .toList();
                sectionClaimRepository.saveAll(claims);
                List<ClaimCitation> citations = new java.util.ArrayList<>();
                for (int claimIndex = 0; claimIndex < claims.size(); claimIndex++) {
                    SectionClaim claim = claims.get(claimIndex);
                    for (ValidatedCitationOutput.ValidatedCitation citation
                            : validatedOutput.claims().get(claimIndex).citations()) {
                        citations.add(ClaimCitation.validated(taskId, version.id(), claim.id(),
                                citation.evidenceId(), citation.sequenceNumber(), citation.supportingQuote(),
                                citation.evidenceStartOffset(), citation.evidenceEndOffset()));
                    }
                }
                claimCitationRepository.saveAll(citations);

                persistedRun.succeed(validatedOutput.bodyMarkdown(), 0);
                runRepository.save(persistedRun);
                WritingTask persistedTask = findTask(taskId);
                if (persistedTask.stage() == WritingStage.OUTLINE_CONFIRMED) {
                    persistedTask.startDrafting();
                    taskRepository.save(persistedTask);
                }
                return new SectionCompletion(persistedRun, version);
            });
            return response(completion.run(), completion.version());
        } catch (Exception exception) {
            return response(failRun(run, "RESULT_PERSISTENCE_ERROR", safeMessage(exception)), null);
        }
    }

    public List<SectionVersionResponse> listSections(String taskIdValue) {
        WritingTaskId taskId = WritingTaskId.from(taskIdValue);
        findTask(taskId);
        return sectionVersionRepository.findByTaskId(taskId).stream().map(this::sectionResponse).toList();
    }

    public List<SectionVersionResponse> listSectionVersions(String taskIdValue, String sectionKey) {
        WritingTask task = findTask(WritingTaskId.from(taskIdValue));
        OutlineSection section = findSection(task, sectionKey);
        return sectionVersionRepository.findByOutlineSectionId(section.id()).stream()
                .map(this::sectionResponse).toList();
    }

    public SectionVersionResponse getSectionVersion(String sectionVersionIdValue) {
        return sectionResponse(sectionVersionRepository.findById(SectionVersionId.from(sectionVersionIdValue))
                .orElseThrow(() -> new SectionVersionNotFoundException(sectionVersionIdValue)));
    }

    public List<SectionClaimResponse> listClaims(String sectionVersionIdValue) {
        SectionVersionId sectionVersionId = SectionVersionId.from(sectionVersionIdValue);
        requireSectionVersion(sectionVersionId);
        return sectionClaimRepository.findBySectionVersionId(sectionVersionId).stream()
                .map(SectionClaimResponse::from).toList();
    }

    public List<ClaimCitationResponse> listCitations(String sectionVersionIdValue) {
        SectionVersionId sectionVersionId = SectionVersionId.from(sectionVersionIdValue);
        SectionVersion version = requireSectionVersion(sectionVersionId);
        Map<org.example.paperaiagent.writing.citation.SectionClaimId, SectionClaim> claims = new HashMap<>();
        sectionClaimRepository.findBySectionVersionId(sectionVersionId)
                .forEach(item -> claims.put(item.id(), item));
        Map<TaskEvidenceId, TaskEvidence> evidence = new HashMap<>();
        evidenceRepository.findByTaskId(version.taskId()).forEach(item -> evidence.put(item.id(), item));
        return claimCitationRepository.findBySectionVersionId(sectionVersionId).stream()
                .map(item -> {
                    SectionClaim claim = claims.get(item.claimId());
                    TaskEvidence source = evidence.get(item.evidenceId());
                    if (claim == null || source == null) {
                        throw new IllegalStateException("Persisted citation relation is incomplete: " + item.id());
                    }
                    return ClaimCitationResponse.from(item, claim, source);
                }).toList();
    }

    public SectionVersionResponse confirmSection(
            String taskIdValue, String sectionKey, String sectionVersionIdValue) {
        WritingTaskId taskId = WritingTaskId.from(taskIdValue);
        Object lock = taskLocks.computeIfAbsent(taskId, ignored -> new Object());
        synchronized (lock) {
            return transactions.required(() -> {
                WritingTask task = findTask(taskId);
                if (task.stage() != WritingStage.DRAFTING && task.stage() != WritingStage.DRAFT_COMPLETED) {
                    throw new WritingConflictException("SECTION_CONFIRM_NOT_ALLOWED",
                            "Section confirmation is not allowed in stage " + task.stage());
                }
                OutlineSection section = findSection(task, sectionKey);
                SectionVersion version = sectionVersionRepository.findById(SectionVersionId.from(sectionVersionIdValue))
                        .orElseThrow(() -> new SectionVersionNotFoundException(sectionVersionIdValue));
                validateVersionOwnership(task, section, version);
                if (version.status() == SectionVersionStatus.CONFIRMED) {
                    return sectionResponse(version);
                }
                if (version.citationSchemaVersion() != 1
                        || version.citationValidationStatus() != CitationValidationStatus.STRUCTURE_VALIDATED
                        || sectionClaimRepository.countBySectionVersionId(version.id()) < 1
                        || claimCitationRepository.countBySectionVersionId(version.id()) < 1) {
                    throw new WritingConflictException("CITATION_VALIDATION_REQUIRED",
                            "Only a structurally validated ClaimCitation V1 section can be confirmed");
                }
                SectionVersion confirmed = sectionVersionRepository.findConfirmedByOutlineSectionId(section.id())
                        .orElse(null);
                if (confirmed != null && !confirmed.id().equals(version.id())) {
                    throw new WritingConflictException("SECTION_ALREADY_CONFIRMED",
                            "The outline section already has a confirmed version");
                }
                version.confirm();
                sectionVersionRepository.save(version);
                List<OutlineSection> writable = outlineSectionRepository
                        .findByOutlineVersionId(task.confirmedOutlineVersionId()).stream()
                        .filter(OutlineSection::writable).toList();
                boolean complete = !writable.isEmpty() && writable.stream().allMatch(item ->
                        sectionVersionRepository.findConfirmedByOutlineSectionId(item.id()).isPresent());
                if (complete && task.stage() != WritingStage.DRAFT_COMPLETED) {
                    task.completeDraft();
                    taskRepository.save(task);
                }
                return sectionResponse(version);
            });
        }
    }

    private SectionAcceptance accept(
            WritingTaskId taskId, String sectionKey, GenerateSectionRequest request, String requestId) {
        Object lock = taskLocks.computeIfAbsent(taskId, ignored -> new Object());
        synchronized (lock) {
            return transactions.required(() -> {
                WritingTask task = findTask(taskId);
                workflow.requireSectionGenerationAllowed(task);
                if (task.confirmedOutlineVersionId() == null) {
                    throw new WritingConflictException("CONFIRMED_OUTLINE_REQUIRED", "A confirmed outline is required");
                }
                OutlineVersion outline = outlineRepository.findById(task.confirmedOutlineVersionId())
                        .orElseThrow(() -> new OutlineNotFoundException(task.confirmedOutlineVersionId().toString()));
                OutlineSection section = findSection(task, sectionKey);
                if (!section.writable()) {
                    throw new WritingConflictException("SECTION_NOT_WRITABLE", "Directory sections cannot be drafted");
                }
                WritingContext context = contextBuilder.build(task, outline, section);
                if (context.evidence().isEmpty()) {
                    throw new WritingConflictException("SECTION_EVIDENCE_REQUIRED",
                            "At least one outline evidence item is required");
                }
                String instruction = normalize(request.instruction(), "根据证据完成本章节正文");
                String prompt = prompt(context, instruction);
                String requestHash = WritingHashes.sha256(String.join("\n", taskId.toString(),
                        outline.id().toString(), section.sectionKey(), instruction,
                        contextBuilder.budgetFingerprint(), "citation-schema=1"));
                TaskRun existing = runRepository.findByTaskIdActionAndRequestId(
                        taskId, TaskRunAction.GENERATE_SECTION, requestId).orElse(null);
                if (existing != null) {
                    requireMatchingHash(existing, requestHash);
                    return SectionAcceptance.idempotent(existing);
                }
                if (runRepository.existsRunningByTaskId(taskId)) {
                    throw new WritingConflictException("TASK_RUN_CONFLICT",
                            "The writing task already has a running agent operation");
                }
                String sessionId = normalize(request.sessionId(), taskId + "-section-" + section.sectionKey());
                TaskRun run = TaskRun.create(taskId, TaskRunAction.GENERATE_SECTION, requestId,
                        requestHash, sessionId, prompt);
                run.start();
                runRepository.save(run);
                return SectionAcceptance.started(run, outline, section, context);
            });
        }
    }

    private OutlineSection findSection(WritingTask task, String sectionKey) {
        if (task.confirmedOutlineVersionId() == null) {
            throw new WritingConflictException("CONFIRMED_OUTLINE_REQUIRED", "A confirmed outline is required");
        }
        String normalized = requireText(sectionKey, "sectionKey");
        return outlineSectionRepository.findByOutlineVersionIdAndSectionKey(
                        task.confirmedOutlineVersionId(), normalized)
                .orElseThrow(() -> new OutlineSectionNotFoundException(normalized));
    }

    private void validateVersionOwnership(WritingTask task, OutlineSection section, SectionVersion version) {
        if (!version.taskId().equals(task.id())
                || !version.outlineVersionId().equals(task.confirmedOutlineVersionId())
                || !version.outlineSectionId().equals(section.id())) {
            throw new WritingConflictException("SECTION_VERSION_MISMATCH",
                    "Section version does not belong to the requested task, outline and section");
        }
    }

    private TaskRun failRun(TaskRun run, String code, String summary) {
        try {
            return transactions.required(() -> {
                TaskRun persisted = runRepository.findById(run.id()).orElse(run);
                if (persisted.status() == TaskRunStatus.RUNNING) persisted.fail(code, summary);
                return runRepository.save(persisted);
            });
        } catch (Exception failure) {
            log.error("Failed to persist terminal state for section run {}", run.id(), failure);
            return run;
        }
    }

    private WritingTask findTask(WritingTaskId id) {
        return taskRepository.findById(id).orElseThrow(() -> new WritingTaskNotFoundException(id.toString()));
    }

    private SectionVersion requireSectionVersion(SectionVersionId id) {
        return sectionVersionRepository.findById(id)
                .orElseThrow(() -> new SectionVersionNotFoundException(id.toString()));
    }

    private static String prompt(WritingContext context, String instruction) {
        String evidence = context.evidence().stream().map(EvidenceContext::renderedText)
                .collect(Collectors.joining("\n\n"));
        String previous = context.previousSections().isEmpty() ? "（无已确认前置章节）"
                : context.previousSections().stream().map(PreviousSectionContext::renderedText)
                .collect(Collectors.joining("\n\n"));
        previous = redactDatabaseIds(previous);
        String objective = redactDatabaseIds(context.targetSection().objective());
        int childHeadingLevel = Math.min(6, context.targetSection().depth() + 1);
        String childHeadingPrefix = "#".repeat(childHeadingLevel);
        return """
                你正在生成论文的一个指定章节。只能使用下面提供的证据别名、证据快照和已确认前置章节。
                不得调用工具、补造来源、输出数据库 UUID 或扩展研究范围。

                论文主题：%s
                写作要求：%s
                当前章节：%s %s
                章节目标：%s
                本次补充要求：%s
                当前章节深度：%d；正文内部标题必须从 %s 开始，且不得重复当前章节标题。

                章节生成上下文证据：
                %s

                已确认前置章节：
                %s

                只返回一个完整 JSON 对象，不要附加解释。允许的结构严格如下：
                {
                  "bodyMarkdown": "正文，不包含当前章节自身标题；每条可验证论述后紧跟 [C1]",
                  "claims": [
                    {
                      "claimKey": "C1",
                      "claimText": "必须是 bodyMarkdown 中唯一出现、且紧邻 [C1] 之前的连续原文",
                      "citations": [
                        {
                          "evidenceAlias": "E1",
                          "supportingQuote": "必须从对应证据内容逐字复制且在该证据中唯一出现的连续片段"
                        }
                      ]
                    }
                  ]
                }

                claimKey 使用 C1、C2……；evidenceAlias 只能使用上文给出的 E1、E2……。
                强约束示例：若 bodyMarkdown 是“ALMT 使用超模态表示完成融合。[C1]”，则 claimText 必须完整等于
                “ALMT 使用超模态表示完成融合。”，必须包含句末标点；[C1] 必须直接位于这段 claimText 之后。
                claimText 的最后一个字符必须就是对应 [Ck] 之前的最后一个正文字符，不得遗漏标点，
                不得只截取句子的一部分，也不得在 claimText 与 [Ck] 之间插入脚注、换行或其他文字。
                supportingQuote 不得改写、翻译或纠正证据原文。证据不足时应缩小论述范围，不得虚构。
                """.formatted(context.task().topic(), context.task().requirements(),
                context.targetSection().sectionKey(), context.targetSection().title(), objective, instruction,
                context.targetSection().depth(), childHeadingPrefix, evidence, previous);
    }

    private SectionGenerationResponse response(TaskRun run, SectionVersion version) {
        return new SectionGenerationResponse(TaskRunResponse.from(run),
                version == null ? null : sectionResponse(version));
    }

    private SectionVersionResponse sectionResponse(SectionVersion version) {
        return SectionVersionResponse.from(version,
                sectionClaimRepository.countBySectionVersionId(version.id()),
                claimCitationRepository.countBySectionVersionId(version.id()));
    }

    private static String redactDatabaseIds(String value) {
        if (value == null || value.isBlank()) return "（未提供）";
        return DATABASE_UUID.matcher(value).replaceAll("[legacy-id-redacted]");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static void requireMatchingHash(TaskRun run, String requestHash) {
        if (!run.requestHash().equals(requestHash)) {
            throw new WritingConflictException("IDEMPOTENCY_CONFLICT",
                    "The idempotency key was already used with different request content");
        }
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private record SectionAcceptance(
            TaskRun run, OutlineVersion outline, OutlineSection section,
            WritingContext context, TaskRun idempotentRun) {
        static SectionAcceptance started(TaskRun run, OutlineVersion outline,
                                         OutlineSection section, WritingContext context) {
            return new SectionAcceptance(run, outline, section, context, null);
        }
        static SectionAcceptance idempotent(TaskRun run) {
            return new SectionAcceptance(null, null, null, null, run);
        }
    }

    private record SectionCompletion(TaskRun run, SectionVersion version) { }
}
