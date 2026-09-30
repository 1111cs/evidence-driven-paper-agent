package org.example.paperaiagent.writing.evidence;

import java.util.List;

public record EvidenceAdoptionPlan(List<TaskEvidence> evidence, int rejectedCandidateCount) {

    public EvidenceAdoptionPlan {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        if (rejectedCandidateCount < 0) {
            throw new IllegalArgumentException("rejectedCandidateCount must not be negative");
        }
    }
}
