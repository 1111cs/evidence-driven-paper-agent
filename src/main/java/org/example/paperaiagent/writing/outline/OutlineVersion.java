package org.example.paperaiagent.writing.outline;

import org.example.paperaiagent.writing.run.TaskRunId;
import org.example.paperaiagent.writing.task.WritingTaskId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class OutlineVersion {
    private final OutlineVersionId id;
    private final WritingTaskId taskId;
    private final TaskRunId generatedByRunId;
    private final int versionNumber;
    private final String title;
    private final String contentSnapshot;
    private final String contentHash;
    private final List<OutlineEvidenceReference> evidenceReferences;
    private final Instant createdAt;
    private OutlineStatus status;
    private Instant confirmedAt;
    private long lockVersion;

    private OutlineVersion(
            OutlineVersionId id, WritingTaskId taskId, TaskRunId generatedByRunId,
            int versionNumber, long lockVersion, OutlineStatus status, String title,
            String contentSnapshot, String contentHash,
            List<OutlineEvidenceReference> evidenceReferences,
            Instant createdAt, Instant confirmedAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.generatedByRunId = Objects.requireNonNull(generatedByRunId, "generatedByRunId");
        if (versionNumber < 1) throw new IllegalArgumentException("versionNumber must be positive");
        if (lockVersion < 0) throw new IllegalArgumentException("lockVersion must not be negative");
        this.versionNumber = versionNumber;
        this.lockVersion = lockVersion;
        this.status = Objects.requireNonNull(status, "status");
        this.title = requireText(title, "title");
        this.contentSnapshot = requireText(contentSnapshot, "contentSnapshot");
        this.contentHash = requireText(contentHash, "contentHash");
        this.evidenceReferences = List.copyOf(evidenceReferences);
        if (this.evidenceReferences.isEmpty()) {
            throw new IllegalArgumentException("OutlineVersion requires evidence");
        }
        long distinctIds = this.evidenceReferences.stream().map(OutlineEvidenceReference::evidenceId).distinct().count();
        if (distinctIds != this.evidenceReferences.size()) {
            throw new IllegalArgumentException("OutlineVersion evidence must not contain duplicates");
        }
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.confirmedAt = confirmedAt;
    }

    public static OutlineVersion draft(
            WritingTaskId taskId, TaskRunId runId, int versionNumber, String title,
            String contentSnapshot, String contentHash, List<OutlineEvidenceReference> evidenceReferences
    ) {
        return new OutlineVersion(OutlineVersionId.newId(), taskId, runId, versionNumber, 0,
                OutlineStatus.DRAFT, title, contentSnapshot, contentHash, evidenceReferences, Instant.now(), null);
    }

    public static OutlineVersion restore(
            OutlineVersionId id, WritingTaskId taskId, TaskRunId runId, int versionNumber,
            long lockVersion, OutlineStatus status, String title, String contentSnapshot,
            String contentHash, List<OutlineEvidenceReference> references,
            Instant createdAt, Instant confirmedAt
    ) {
        return new OutlineVersion(id, taskId, runId, versionNumber, lockVersion, status, title,
                contentSnapshot, contentHash, references, createdAt, confirmedAt);
    }

    public synchronized void confirm() {
        if (status == OutlineStatus.CONFIRMED) return;
        if (status != OutlineStatus.DRAFT) throw new IllegalStateException("Only a draft outline can be confirmed");
        status = OutlineStatus.CONFIRMED;
        confirmedAt = Instant.now();
        lockVersion++;
    }

    public OutlineVersionId id() { return id; }
    public WritingTaskId taskId() { return taskId; }
    public TaskRunId generatedByRunId() { return generatedByRunId; }
    public int versionNumber() { return versionNumber; }
    public String title() { return title; }
    public String contentSnapshot() { return contentSnapshot; }
    public String contentHash() { return contentHash; }
    public List<OutlineEvidenceReference> evidenceReferences() { return evidenceReferences; }
    public Instant createdAt() { return createdAt; }
    public synchronized OutlineStatus status() { return status; }
    public synchronized Instant confirmedAt() { return confirmedAt; }
    public synchronized long lockVersion() { return lockVersion; }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
}
