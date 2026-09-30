package org.example.paperaiagent.writing.application.dto;

import org.example.paperaiagent.writing.citation.CitationValidationStatus;
import org.example.paperaiagent.writing.citation.SectionClaim;

import java.time.Instant;

public record SectionClaimResponse(
        String claimId,
        String taskId,
        String sectionVersionId,
        String claimKey,
        String claimText,
        int startOffset,
        int endOffset,
        CitationValidationStatus validationStatus,
        Instant createdAt
) {
    public static SectionClaimResponse from(SectionClaim claim) {
        return new SectionClaimResponse(claim.id().toString(), claim.taskId().toString(),
                claim.sectionVersionId().toString(), claim.claimKey(), claim.claimText(),
                claim.startOffset(), claim.endOffset(), claim.validationStatus(), claim.createdAt());
    }
}
