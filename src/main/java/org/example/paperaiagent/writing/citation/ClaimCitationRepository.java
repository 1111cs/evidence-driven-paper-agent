package org.example.paperaiagent.writing.citation;

import org.example.paperaiagent.writing.section.SectionVersionId;

import java.util.List;

public interface ClaimCitationRepository {
    List<ClaimCitation> saveAll(List<ClaimCitation> citations);
    List<ClaimCitation> findBySectionVersionId(SectionVersionId sectionVersionId);
    long countBySectionVersionId(SectionVersionId sectionVersionId);
}
