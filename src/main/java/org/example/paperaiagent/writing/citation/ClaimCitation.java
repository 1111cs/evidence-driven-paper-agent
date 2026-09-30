package org.example.paperaiagent.writing.citation;

import org.example.paperaiagent.writing.evidence.TaskEvidenceId;
import org.example.paperaiagent.writing.section.SectionVersionId;
import org.example.paperaiagent.writing.task.WritingTaskId;

import java.time.Instant;
import java.util.Objects;

public record ClaimCitation(
        ClaimCitationId id,
        WritingTaskId taskId,
        SectionVersionId sectionVersionId,
        SectionClaimId claimId,
        TaskEvidenceId evidenceId,
        int sequenceNumber,
        String supportingQuote,
        int evidenceStartOffset,
        int evidenceEndOffset,
        Instant createdAt
) {
    public ClaimCitation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(sectionVersionId, "sectionVersionId");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(evidenceId, "evidenceId");
        if (sequenceNumber < 1) throw new IllegalArgumentException("sequenceNumber must be positive");
        Objects.requireNonNull(supportingQuote, "supportingQuote");
        if (supportingQuote.isBlank()) throw new IllegalArgumentException("supportingQuote must not be blank");
        if (evidenceStartOffset < 0 || evidenceEndOffset <= evidenceStartOffset) {
            throw new IllegalArgumentException("evidence offsets must form a non-empty range");
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static ClaimCitation validated(
            WritingTaskId taskId, SectionVersionId sectionVersionId, SectionClaimId claimId,
            TaskEvidenceId evidenceId, int sequenceNumber, String supportingQuote,
            int evidenceStartOffset, int evidenceEndOffset) {
        return new ClaimCitation(ClaimCitationId.newId(), taskId, sectionVersionId, claimId,
                evidenceId, sequenceNumber, supportingQuote, evidenceStartOffset,
                evidenceEndOffset, Instant.now());
    }
}
