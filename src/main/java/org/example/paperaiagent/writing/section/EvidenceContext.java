package org.example.paperaiagent.writing.section;

import org.example.paperaiagent.writing.evidence.TaskEvidenceId;
import org.example.paperaiagent.writing.task.WritingTaskId;

import java.util.Objects;

public record EvidenceContext(
        TaskEvidenceId evidenceId,
        WritingTaskId taskId,
        int sequenceNumber,
        String alias,
        String contentSnapshot,
        String renderedText
) {
    public EvidenceContext {
        Objects.requireNonNull(evidenceId, "evidenceId");
        Objects.requireNonNull(taskId, "taskId");
        if (sequenceNumber < 1) throw new IllegalArgumentException("sequenceNumber must be positive");
        Objects.requireNonNull(alias, "alias");
        if (!alias.equals("E" + sequenceNumber)) {
            throw new IllegalArgumentException("alias must match the sequence number");
        }
        Objects.requireNonNull(contentSnapshot, "contentSnapshot");
        Objects.requireNonNull(renderedText, "renderedText");
    }
}
