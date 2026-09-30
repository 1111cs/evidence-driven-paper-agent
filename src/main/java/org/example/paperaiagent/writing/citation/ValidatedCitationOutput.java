package org.example.paperaiagent.writing.citation;

import org.example.paperaiagent.writing.evidence.TaskEvidenceId;

import java.util.List;

public record ValidatedCitationOutput(String bodyMarkdown, List<ValidatedClaim> claims) {
    public record ValidatedClaim(
            String claimKey, String claimText, int startOffset, int endOffset,
            List<ValidatedCitation> citations) { }

    public record ValidatedCitation(
            TaskEvidenceId evidenceId, int sequenceNumber, String supportingQuote,
            int evidenceStartOffset, int evidenceEndOffset) { }
}
