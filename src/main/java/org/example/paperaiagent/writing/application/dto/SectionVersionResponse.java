package org.example.paperaiagent.writing.application.dto;

import org.example.paperaiagent.writing.citation.CitationValidationStatus;
import org.example.paperaiagent.writing.section.SectionVersion;
import org.example.paperaiagent.writing.section.SectionVersionStatus;

import java.time.Instant;
import java.util.List;

public record SectionVersionResponse(
        String sectionVersionId,
        String taskId,
        String outlineVersionId,
        String outlineSectionId,
        String generatedByRunId,
        int versionNumber,
        long lockVersion,
        SectionVersionStatus status,
        String sectionTitle,
        String contentSnapshot,
        String contentHash,
        List<String> contextEvidenceIds,
        int citationSchemaVersion,
        CitationValidationStatus citationValidationStatus,
        long claimCount,
        long citationCount,
        Instant createdAt,
        Instant confirmedAt
) {
    public static SectionVersionResponse from(SectionVersion version, long claimCount, long citationCount) {
        return new SectionVersionResponse(version.id().toString(), version.taskId().toString(),
                version.outlineVersionId().toString(), version.outlineSectionId().toString(),
                version.generatedByRunId().toString(), version.versionNumber(), version.lockVersion(),
                version.status(), version.sectionTitle(), version.contentSnapshot(), version.contentHash(),
                version.evidenceReferences().stream().map(item -> item.evidenceId().toString()).toList(),
                version.citationSchemaVersion(), version.citationValidationStatus(), claimCount, citationCount,
                version.createdAt(), version.confirmedAt());
    }

    public static SectionVersionResponse from(SectionVersion version) {
        return from(version, 0, 0);
    }
}
