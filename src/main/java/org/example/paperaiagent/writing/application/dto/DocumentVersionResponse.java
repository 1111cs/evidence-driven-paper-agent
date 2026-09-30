package org.example.paperaiagent.writing.application.dto;

import org.example.paperaiagent.writing.document.DocumentVersion;

import java.time.Instant;

public record DocumentVersionResponse(
        String documentVersionId,
        String taskId,
        String outlineVersionId,
        int versionNumber,
        String format,
        String assemblerVersion,
        String contentHash,
        String sourceFingerprint,
        int outlineSectionCount,
        int sectionCount,
        int claimCount,
        Instant createdAt
) {
    public static DocumentVersionResponse from(
            DocumentVersion value, int outlineSectionCount, int sectionCount, int claimCount) {
        return new DocumentVersionResponse(value.id().toString(), value.taskId().toString(),
                value.outlineVersionId().toString(), value.versionNumber(), value.format().name(),
                value.assemblerVersion(), value.contentHash(), value.sourceFingerprint(),
                outlineSectionCount, sectionCount, claimCount, value.createdAt());
    }
}
