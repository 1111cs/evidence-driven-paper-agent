package org.example.paperaiagent.writing.section;

import org.example.paperaiagent.writing.evidence.TaskEvidenceId;

import java.util.Objects;

public record SectionEvidenceReference(TaskEvidenceId evidenceId, int sequenceNumber) {
    public SectionEvidenceReference {
        Objects.requireNonNull(evidenceId, "evidenceId");
        if (sequenceNumber < 1) throw new IllegalArgumentException("sequenceNumber must be positive");
    }
}
