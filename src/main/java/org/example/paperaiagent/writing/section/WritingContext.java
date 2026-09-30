package org.example.paperaiagent.writing.section;

import org.example.paperaiagent.writing.outline.OutlineSection;
import org.example.paperaiagent.writing.outline.OutlineVersion;
import org.example.paperaiagent.writing.task.WritingTask;

import java.util.List;
import java.util.Objects;

public record WritingContext(
        WritingTask task,
        OutlineVersion confirmedOutline,
        OutlineSection targetSection,
        List<EvidenceContext> evidence,
        List<PreviousSectionContext> previousSections,
        int evidenceCharacterCount,
        int previousSectionCharacterCount
) {
    public WritingContext {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(confirmedOutline, "confirmedOutline");
        Objects.requireNonNull(targetSection, "targetSection");
        evidence = List.copyOf(evidence);
        previousSections = List.copyOf(previousSections);
    }
}
