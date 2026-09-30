package org.example.paperaiagent.writing.section;

import org.example.paperaiagent.writing.citation.CitationValidationStatus;
import org.example.paperaiagent.writing.outline.OutlineSectionId;
import org.example.paperaiagent.writing.outline.OutlineVersionId;
import org.example.paperaiagent.writing.run.TaskRunId;
import org.example.paperaiagent.writing.task.WritingTaskId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class SectionVersion {
    private final SectionVersionId id;
    private final WritingTaskId taskId;
    private final OutlineVersionId outlineVersionId;
    private final OutlineSectionId outlineSectionId;
    private final TaskRunId generatedByRunId;
    private final int versionNumber;
    private final String sectionTitle;
    private final String contentSnapshot;
    private final String contentHash;
    private final int citationSchemaVersion;
    private final CitationValidationStatus citationValidationStatus;
    private final List<SectionEvidenceReference> evidenceReferences;
    private final Instant createdAt;
    private long lockVersion;
    private SectionVersionStatus status;
    private Instant confirmedAt;

    private SectionVersion(
            SectionVersionId id, WritingTaskId taskId, OutlineVersionId outlineVersionId,
            OutlineSectionId outlineSectionId, TaskRunId generatedByRunId, int versionNumber,
            long lockVersion, SectionVersionStatus status, String sectionTitle,
            String contentSnapshot, String contentHash, int citationSchemaVersion,
            CitationValidationStatus citationValidationStatus,
            List<SectionEvidenceReference> evidenceReferences,
            Instant createdAt, Instant confirmedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.outlineVersionId = Objects.requireNonNull(outlineVersionId, "outlineVersionId");
        this.outlineSectionId = Objects.requireNonNull(outlineSectionId, "outlineSectionId");
        this.generatedByRunId = Objects.requireNonNull(generatedByRunId, "generatedByRunId");
        if (versionNumber < 1) throw new IllegalArgumentException("versionNumber must be positive");
        if (lockVersion < 0) throw new IllegalArgumentException("lockVersion must not be negative");
        this.versionNumber = versionNumber;
        this.lockVersion = lockVersion;
        this.status = Objects.requireNonNull(status, "status");
        this.sectionTitle = requireText(sectionTitle, "sectionTitle");
        this.contentSnapshot = requireText(contentSnapshot, "contentSnapshot");
        this.contentHash = requireText(contentHash, "contentHash");
        if (citationSchemaVersion < 0) throw new IllegalArgumentException("citationSchemaVersion must not be negative");
        this.citationSchemaVersion = citationSchemaVersion;
        this.citationValidationStatus = Objects.requireNonNull(citationValidationStatus, "citationValidationStatus");
        this.evidenceReferences = List.copyOf(evidenceReferences);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.confirmedAt = confirmedAt;
    }

    public static SectionVersion draft(
            WritingTaskId taskId, OutlineVersionId outlineVersionId, OutlineSectionId outlineSectionId,
            TaskRunId generatedByRunId, int versionNumber, String sectionTitle,
            String contentSnapshot, String contentHash, List<SectionEvidenceReference> evidenceReferences) {
        return new SectionVersion(SectionVersionId.newId(), taskId, outlineVersionId, outlineSectionId,
                generatedByRunId, versionNumber, 0, SectionVersionStatus.DRAFT, sectionTitle,
                contentSnapshot, contentHash, 0, CitationValidationStatus.LEGACY_UNVALIDATED,
                evidenceReferences, Instant.now(), null);
    }

    public static SectionVersion citationValidatedDraft(
            WritingTaskId taskId, OutlineVersionId outlineVersionId, OutlineSectionId outlineSectionId,
            TaskRunId generatedByRunId, int versionNumber, String sectionTitle,
            String contentSnapshot, String contentHash, List<SectionEvidenceReference> evidenceReferences) {
        return new SectionVersion(SectionVersionId.newId(), taskId, outlineVersionId, outlineSectionId,
                generatedByRunId, versionNumber, 0, SectionVersionStatus.DRAFT, sectionTitle,
                contentSnapshot, contentHash, 1, CitationValidationStatus.STRUCTURE_VALIDATED,
                evidenceReferences, Instant.now(), null);
    }

    public static SectionVersion restore(
            SectionVersionId id, WritingTaskId taskId, OutlineVersionId outlineVersionId,
            OutlineSectionId outlineSectionId, TaskRunId generatedByRunId, int versionNumber,
            long lockVersion, SectionVersionStatus status, String sectionTitle,
            String contentSnapshot, String contentHash, int citationSchemaVersion,
            CitationValidationStatus citationValidationStatus,
            List<SectionEvidenceReference> evidenceReferences,
            Instant createdAt, Instant confirmedAt) {
        return new SectionVersion(id, taskId, outlineVersionId, outlineSectionId, generatedByRunId,
                versionNumber, lockVersion, status, sectionTitle, contentSnapshot, contentHash,
                citationSchemaVersion, citationValidationStatus, evidenceReferences, createdAt, confirmedAt);
    }

    public synchronized void confirm() {
        if (status == SectionVersionStatus.CONFIRMED) return;
        if (citationSchemaVersion != 1
                || citationValidationStatus != CitationValidationStatus.STRUCTURE_VALIDATED) {
            throw new IllegalStateException("CITATION_VALIDATION_REQUIRED");
        }
        status = SectionVersionStatus.CONFIRMED;
        confirmedAt = Instant.now();
        lockVersion++;
    }

    public SectionVersionId id() { return id; }
    public WritingTaskId taskId() { return taskId; }
    public OutlineVersionId outlineVersionId() { return outlineVersionId; }
    public OutlineSectionId outlineSectionId() { return outlineSectionId; }
    public TaskRunId generatedByRunId() { return generatedByRunId; }
    public int versionNumber() { return versionNumber; }
    public synchronized long lockVersion() { return lockVersion; }
    public synchronized SectionVersionStatus status() { return status; }
    public String sectionTitle() { return sectionTitle; }
    public String contentSnapshot() { return contentSnapshot; }
    public String contentHash() { return contentHash; }
    public int citationSchemaVersion() { return citationSchemaVersion; }
    public CitationValidationStatus citationValidationStatus() { return citationValidationStatus; }
    public List<SectionEvidenceReference> evidenceReferences() { return evidenceReferences; }
    public Instant createdAt() { return createdAt; }
    public synchronized Instant confirmedAt() { return confirmedAt; }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
}
