package org.example.paperaiagent.writing.run;

import org.example.paperaiagent.writing.task.WritingTaskId;

import java.time.Instant;
import java.util.Objects;

public final class TaskRun {

    private final TaskRunId id;
    private final WritingTaskId taskId;
    private final TaskRunAction action;
    private final String requestId;
    private final String requestHash;
    private final String runtimeSessionId;
    private final String inputSnapshot;
    private final Instant createdAt;
    private TaskRunStatus status;
    private Instant startedAt;
    private Instant finishedAt;
    private String finalAnswer;
    private String errorCode;
    private String errorSummary;
    private int adoptedEvidenceCount;

    private TaskRun(
            TaskRunId id,
            WritingTaskId taskId,
            TaskRunAction action,
            String requestId,
            String requestHash,
            String runtimeSessionId,
            String inputSnapshot,
            TaskRunStatus status,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            String finalAnswer,
            String errorCode,
            String errorSummary,
            int adoptedEvidenceCount
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.action = Objects.requireNonNull(action, "action");
        this.requestId = requireText(requestId, "requestId");
        this.requestHash = requireText(requestHash, "requestHash");
        this.runtimeSessionId = requireText(runtimeSessionId, "runtimeSessionId");
        this.inputSnapshot = requireText(inputSnapshot, "inputSnapshot");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.finalAnswer = finalAnswer;
        this.errorCode = errorCode;
        this.errorSummary = errorSummary;
        if (adoptedEvidenceCount < 0) {
            throw new IllegalArgumentException("adoptedEvidenceCount must not be negative");
        }
        this.adoptedEvidenceCount = adoptedEvidenceCount;
    }

    public static TaskRun create(
            WritingTaskId taskId,
            TaskRunAction action,
            String requestId,
            String requestHash,
            String runtimeSessionId,
            String inputSnapshot
    ) {
        return new TaskRun(
                TaskRunId.newId(), taskId, action, requestId, requestHash, runtimeSessionId, inputSnapshot,
                TaskRunStatus.CREATED, Instant.now(), null, null, null, null, null, 0
        );
    }

    public static TaskRun create(
            WritingTaskId taskId,
            String requestId,
            String runtimeSessionId,
            String inputSnapshot
    ) {
        return create(taskId, TaskRunAction.RESEARCH, requestId, legacyHash(requestId, inputSnapshot),
                runtimeSessionId, inputSnapshot);
    }

    public static TaskRun restore(
            TaskRunId id,
            WritingTaskId taskId,
            TaskRunAction action,
            String requestId,
            String requestHash,
            String runtimeSessionId,
            String inputSnapshot,
            TaskRunStatus status,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            String finalAnswer,
            String errorCode,
            String errorSummary,
            int adoptedEvidenceCount
    ) {
        return new TaskRun(
                id, taskId, action, requestId, requestHash, runtimeSessionId, inputSnapshot, status, createdAt, startedAt,
                finishedAt, finalAnswer, errorCode, errorSummary, adoptedEvidenceCount
        );
    }

    public synchronized void start() {
        requireStatus(TaskRunStatus.CREATED);
        status = TaskRunStatus.RUNNING;
        startedAt = Instant.now();
    }

    public synchronized void succeed(String finalAnswer, int adoptedEvidenceCount) {
        requireStatus(TaskRunStatus.RUNNING);
        if (adoptedEvidenceCount < 0) {
            throw new IllegalArgumentException("adoptedEvidenceCount must not be negative");
        }
        this.status = TaskRunStatus.SUCCEEDED;
        this.finalAnswer = finalAnswer;
        this.adoptedEvidenceCount = adoptedEvidenceCount;
        this.finishedAt = Instant.now();
    }

    public synchronized void fail(String errorCode, String errorSummary) {
        requireStatus(TaskRunStatus.RUNNING);
        this.status = TaskRunStatus.FAILED;
        this.errorCode = requireText(errorCode, "errorCode");
        this.errorSummary = safeSummary(errorSummary);
        this.finishedAt = Instant.now();
    }

    public TaskRunId id() { return id; }
    public WritingTaskId taskId() { return taskId; }
    public TaskRunAction action() { return action; }
    public String requestId() { return requestId; }
    public String requestHash() { return requestHash; }
    public String runtimeSessionId() { return runtimeSessionId; }
    public String inputSnapshot() { return inputSnapshot; }

    /** @deprecated use {@link #inputSnapshot()} */
    @Deprecated
    public String researchPrompt() { return inputSnapshot; }
    public Instant createdAt() { return createdAt; }
    public synchronized TaskRunStatus status() { return status; }
    public synchronized Instant startedAt() { return startedAt; }
    public synchronized Instant finishedAt() { return finishedAt; }
    public synchronized String finalAnswer() { return finalAnswer; }
    public synchronized String errorCode() { return errorCode; }
    public synchronized String errorSummary() { return errorSummary; }
    public synchronized int adoptedEvidenceCount() { return adoptedEvidenceCount; }

    private void requireStatus(TaskRunStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("TaskRun " + id + " is " + status + ", expected " + expected);
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String safeSummary(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown failure";
        }
        String normalized = value.trim();
        return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000);
    }

    private static String legacyHash(String requestId, String prompt) {
        return Integer.toHexString(Objects.hash(requestId, prompt));
    }
}
