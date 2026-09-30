package org.example.paperaiagent.writing.application.dto;

import org.example.paperaiagent.writing.outline.OutlineSection;

public record OutlineSectionResponse(
        String outlineSectionId,
        String outlineVersionId,
        String sectionKey,
        String parentSectionKey,
        int sequenceNumber,
        int depth,
        String title,
        String objective,
        boolean writable
) {
    public static OutlineSectionResponse from(OutlineSection section) {
        return new OutlineSectionResponse(section.id().toString(), section.outlineVersionId().toString(),
                section.sectionKey(), section.parentSectionKey(), section.sequenceNumber(), section.depth(),
                section.title(), section.objective(), section.writable());
    }
}
