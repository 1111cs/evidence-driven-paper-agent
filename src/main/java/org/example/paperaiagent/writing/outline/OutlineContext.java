package org.example.paperaiagent.writing.outline;

import org.example.paperaiagent.writing.evidence.TaskEvidence;

import java.util.List;

public record OutlineContext(List<TaskEvidence> evidence, String renderedEvidence) {
    public OutlineContext {
        evidence = List.copyOf(evidence);
        renderedEvidence = renderedEvidence == null ? "" : renderedEvidence;
    }
}
