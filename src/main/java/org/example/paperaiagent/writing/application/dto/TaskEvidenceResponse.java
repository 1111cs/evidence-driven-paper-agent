package org.example.paperaiagent.writing.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.example.paperaiagent.writing.evidence.EvidenceSourceType;
import org.example.paperaiagent.writing.evidence.PageNumberingScheme;
import org.example.paperaiagent.writing.evidence.TaskEvidence;

import java.time.Instant;
import java.util.List;

public record TaskEvidenceResponse(
        String evidenceId,
        String taskId,
        String adoptedRunId,
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

    public static TaskEvidenceResponse from(TaskEvidence evidence) {
        return new TaskEvidenceResponse(
                evidence.id().toString(),
                evidence.taskId().toString(),
                evidence.adoptedRunId().toString(),
                evidence.sourceType(),
                evidence.documentId(),
                evidence.documentName(),
                evidence.section(),
                evidence.sourcePages(),
                evidence.pageNumberingScheme(),
                evidence.pageDisplayText(),
                evidence.contentSnapshot(),
                evidence.contentHash(),
                evidence.sourceCandidateId(),
                evidence.stableKey(),
                evidence.score(),
                evidence.adoptedAt()
        );
    }

    /** Compatibility accessor for older clients; sourcePages is the authoritative field. */
    @JsonIgnore
    public List<Integer> pages() {
        return sourcePages;
    }
}
