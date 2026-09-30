package org.example.paperaiagent.writing.evidence;

import org.example.paperaiagent.writing.run.TaskRunId;
import org.example.paperaiagent.writing.task.WritingTaskId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record TaskEvidence(
        TaskEvidenceId id,
        WritingTaskId taskId,
        TaskRunId adoptedRunId,
        EvidenceSourceType sourceType,
        String documentId,
        String documentName,
        String section,
        List<Integer> sourcePages,
        PageNumberingScheme pageNumberingScheme,
        String pageDisplayText,
        String contentSnapshot,
        String contentHash,
        String sourceCandidateId,
        String stableKey,
        double score,
        Instant adoptedAt
) {

    public TaskEvidence {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(adoptedRunId, "adoptedRunId");
        Objects.requireNonNull(sourceType, "sourceType");
        documentId = requireText(documentId, "documentId");
        documentName = requireText(documentName, "documentName");
        section = section == null ? "" : section.trim();
        sourcePages = sourcePages == null ? List.of() : List.copyOf(sourcePages);
        Objects.requireNonNull(pageNumberingScheme, "pageNumberingScheme");
        pageDisplayText = pageDisplayText == null ? "" : pageDisplayText.trim();
        if (section.isBlank() && sourcePages.isEmpty()) {
            throw new IllegalArgumentException("Evidence requires a section or source page");
        }
        contentSnapshot = requireText(contentSnapshot, "contentSnapshot");
        contentHash = requireText(contentHash, "contentHash");
        sourceCandidateId = requireText(sourceCandidateId, "sourceCandidateId");
        stableKey = requireText(stableKey, "stableKey");
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("score must be finite");
        }
        Objects.requireNonNull(adoptedAt, "adoptedAt");
    }

    /** Compatibility accessor for callers migrating from the old ambiguous field name. */
    public List<Integer> pages() {
        return sourcePages;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
