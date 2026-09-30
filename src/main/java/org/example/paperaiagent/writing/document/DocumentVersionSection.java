package org.example.paperaiagent.writing.document;

import org.example.paperaiagent.writing.outline.OutlineSectionId;
import org.example.paperaiagent.writing.outline.OutlineVersionId;
import org.example.paperaiagent.writing.section.SectionVersionId;
import org.example.paperaiagent.writing.task.WritingTaskId;

import java.util.Objects;

public record DocumentVersionSection(
        DocumentVersionId documentVersionId,
        WritingTaskId taskId,
        OutlineVersionId outlineVersionId,
        OutlineSectionId outlineSectionId,
        SectionVersionId sectionVersionId,
        int sequenceNumber
) {
    public DocumentVersionSection {
        Objects.requireNonNull(documentVersionId, "documentVersionId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(outlineVersionId, "outlineVersionId");
        Objects.requireNonNull(outlineSectionId, "outlineSectionId");
        Objects.requireNonNull(sectionVersionId, "sectionVersionId");
        if (sequenceNumber < 1) throw new IllegalArgumentException("sequenceNumber must be positive");
    }
}
