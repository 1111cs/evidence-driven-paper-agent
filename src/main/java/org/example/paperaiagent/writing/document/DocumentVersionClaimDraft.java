package org.example.paperaiagent.writing.document;

import org.example.paperaiagent.writing.citation.SectionClaimId;
import org.example.paperaiagent.writing.section.SectionVersionId;

public record DocumentVersionClaimDraft(
        SectionVersionId sectionVersionId,
        SectionClaimId claimId,
        int displayNumber
) { }
