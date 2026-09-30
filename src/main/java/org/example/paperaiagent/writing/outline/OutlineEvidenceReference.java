package org.example.paperaiagent.writing.outline;

import org.example.paperaiagent.writing.evidence.TaskEvidenceId;

import java.util.Objects;

public record OutlineEvidenceReference(TaskEvidenceId evidenceId, int sequenceNumber) {
    public OutlineEvidenceReference {
        Objects.requireNonNull(evidenceId, "evidenceId");
        if (sequenceNumber < 1) {
            throw new IllegalArgumentException("sequenceNumber must be positive");
        }
    }
}
