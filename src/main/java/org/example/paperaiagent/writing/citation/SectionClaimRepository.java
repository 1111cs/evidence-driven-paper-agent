package org.example.paperaiagent.writing.citation;

import org.example.paperaiagent.writing.section.SectionVersionId;

import java.util.List;

public interface SectionClaimRepository {
    List<SectionClaim> saveAll(List<SectionClaim> claims);
    List<SectionClaim> findBySectionVersionId(SectionVersionId sectionVersionId);
    long countBySectionVersionId(SectionVersionId sectionVersionId);
}
