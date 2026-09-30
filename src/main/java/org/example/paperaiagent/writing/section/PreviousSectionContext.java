package org.example.paperaiagent.writing.section;

import org.example.paperaiagent.writing.outline.OutlineSectionId;

import java.util.Objects;

public record PreviousSectionContext(
        OutlineSectionId outlineSectionId,
        String sectionKey,
        int sequenceNumber,
        String renderedText
) {
    public PreviousSectionContext {
        Objects.requireNonNull(outlineSectionId, "outlineSectionId");
        Objects.requireNonNull(sectionKey, "sectionKey");
        Objects.requireNonNull(renderedText, "renderedText");
    }
}
