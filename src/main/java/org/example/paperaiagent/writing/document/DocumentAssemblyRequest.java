package org.example.paperaiagent.writing.document;

import org.example.paperaiagent.writing.task.WritingTaskId;

import java.time.Instant;
import java.util.Objects;

public record DocumentAssemblyRequest(
        WritingTaskId taskId,
        String requestId,
        String requestHash,
        DocumentVersionId documentVersionId,
        Instant createdAt
) {
    public DocumentAssemblyRequest {
        Objects.requireNonNull(taskId, "taskId");
        requestId = requireText(requestId, "requestId");
        requestHash = requireText(requestHash, "requestHash");
        Objects.requireNonNull(documentVersionId, "documentVersionId");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static DocumentAssemblyRequest create(
            WritingTaskId taskId, String requestId, String requestHash, DocumentVersionId documentVersionId) {
        return new DocumentAssemblyRequest(taskId, requestId, requestHash, documentVersionId, Instant.now());
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
}
