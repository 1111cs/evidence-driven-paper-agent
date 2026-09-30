package org.example.paperaiagent.writing.citation;

import org.example.paperaiagent.writing.section.SectionVersionId;
import org.example.paperaiagent.writing.task.WritingTaskId;

import java.time.Instant;
import java.util.Objects;

public record SectionClaim(
        SectionClaimId id,
        WritingTaskId taskId,
        SectionVersionId sectionVersionId,
        String claimKey,
        String claimText,
        int startOffset,
        int endOffset,
        CitationValidationStatus validationStatus,
        Instant createdAt
) {
    public SectionClaim {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(sectionVersionId, "sectionVersionId");
        claimKey = requireText(claimKey, "claimKey");
        claimText = requireText(claimText, "claimText");
        if (startOffset < 0 || endOffset <= startOffset) {
            throw new IllegalArgumentException("claim offsets must form a non-empty range");
        }
        Objects.requireNonNull(validationStatus, "validationStatus");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static SectionClaim validated(
            WritingTaskId taskId, SectionVersionId sectionVersionId,
            String claimKey, String claimText, int startOffset, int endOffset) {
        return new SectionClaim(SectionClaimId.newId(), taskId, sectionVersionId,
                claimKey, claimText, startOffset, endOffset,
                CitationValidationStatus.STRUCTURE_VALIDATED, Instant.now());
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
