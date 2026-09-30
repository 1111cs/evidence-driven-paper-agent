package org.example.paperaiagent.writing.document;

import org.example.paperaiagent.writing.outline.OutlineVersionId;
import org.example.paperaiagent.writing.task.WritingTaskId;

import java.time.Instant;
import java.util.Objects;

public record DocumentVersion(
        DocumentVersionId id,
        WritingTaskId taskId,
        OutlineVersionId outlineVersionId,
        int versionNumber,
        DocumentFormat format,
        String assemblerVersion,
        String contentSnapshot,
        String contentHash,
        String sourceFingerprint,
        Instant createdAt
) {
    public DocumentVersion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(outlineVersionId, "outlineVersionId");
        if (versionNumber < 1) throw new IllegalArgumentException("versionNumber must be positive");
        Objects.requireNonNull(format, "format");
        assemblerVersion = requireText(assemblerVersion, "assemblerVersion");
        contentSnapshot = requireText(contentSnapshot, "contentSnapshot");
        contentHash = requireText(contentHash, "contentHash");
        sourceFingerprint = requireText(sourceFingerprint, "sourceFingerprint");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static DocumentVersion create(
            WritingTaskId taskId, OutlineVersionId outlineVersionId, int versionNumber,
            DocumentFormat format, String assemblerVersion, String contentSnapshot,
            String contentHash, String sourceFingerprint) {
        return new DocumentVersion(DocumentVersionId.newId(), taskId, outlineVersionId, versionNumber,
                format, assemblerVersion, contentSnapshot, contentHash, sourceFingerprint, Instant.now());
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
