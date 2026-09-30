package org.example.paperaiagent.writing.application.dto;

import org.example.paperaiagent.writing.citation.ClaimCitation;
import org.example.paperaiagent.writing.citation.SectionClaim;
import org.example.paperaiagent.writing.evidence.TaskEvidence;

import java.time.Instant;
import java.util.List;

public record ClaimCitationResponse(
        String citationId,
        String claimId,
        String claimKey,
        String claimText,
        String evidenceId,
        String documentName,
        String section,
        List<Integer> sourcePages,
        String pageDisplayText,
        int sequenceNumber,
        String supportingQuote,
        int evidenceStartOffset,
        int evidenceEndOffset,
        Instant createdAt
) {
    public static ClaimCitationResponse from(
            ClaimCitation citation, SectionClaim claim, TaskEvidence evidence) {
        return new ClaimCitationResponse(citation.id().toString(), claim.id().toString(),
                claim.claimKey(), claim.claimText(), evidence.id().toString(), evidence.documentName(),
                evidence.section(), evidence.sourcePages(), evidence.pageDisplayText(),
                citation.sequenceNumber(), citation.supportingQuote(), citation.evidenceStartOffset(),
                citation.evidenceEndOffset(), citation.createdAt());
    }
}
