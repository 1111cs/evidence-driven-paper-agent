package org.example.paperaiagent.writing.document;

import org.example.paperaiagent.writing.citation.SectionClaimId;
import org.example.paperaiagent.writing.section.SectionVersionId;
import org.example.paperaiagent.writing.task.WritingTaskId;

import java.util.Objects;

public record DocumentVersionClaim(
        DocumentVersionId documentVersionId,
        WritingTaskId taskId,
        SectionVersionId sectionVersionId,
        SectionClaimId claimId,
        int displayNumber
) {
    public DocumentVersionClaim {
        Objects.requireNonNull(documentVersionId, "documentVersionId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(sectionVersionId, "sectionVersionId");
        Objects.requireNonNull(claimId, "claimId");
        if (displayNumber < 1) throw new IllegalArgumentException("displayNumber must be positive");
    }
}
