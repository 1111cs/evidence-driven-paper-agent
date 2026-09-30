package org.example.paperaiagent.writing.document;

import org.example.paperaiagent.writing.outline.OutlineSectionId;
import org.example.paperaiagent.writing.section.SectionVersionId;

public record DocumentVersionSectionDraft(
        OutlineSectionId outlineSectionId,
        SectionVersionId sectionVersionId,
        int sequenceNumber
) { }
