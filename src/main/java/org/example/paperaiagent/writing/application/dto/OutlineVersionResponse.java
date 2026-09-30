package org.example.paperaiagent.writing.application.dto;

import org.example.paperaiagent.writing.outline.OutlineEvidenceReference;
import org.example.paperaiagent.writing.outline.OutlineStatus;
import org.example.paperaiagent.writing.outline.OutlineVersion;

import java.time.Instant;
import java.util.List;

public record OutlineVersionResponse(
        String outlineVersionId,
        String taskId,
        String generatedByRunId,
        int versionNumber,
        long lockVersion,
        OutlineStatus status,
        String title,
        String contentSnapshot,
        String contentHash,
        List<String> evidenceIds,
        Instant createdAt,
        Instant confirmedAt
) {
    public static OutlineVersionResponse from(OutlineVersion outline) {
        return new OutlineVersionResponse(
                outline.id().toString(), outline.taskId().toString(), outline.generatedByRunId().toString(),
                outline.versionNumber(), outline.lockVersion(), outline.status(), outline.title(),
                outline.contentSnapshot(), outline.contentHash(),
                outline.evidenceReferences().stream()
                        .sorted(java.util.Comparator.comparingInt(OutlineEvidenceReference::sequenceNumber))
                        .map(reference -> reference.evidenceId().toString()).toList(),
                outline.createdAt(), outline.confirmedAt()
        );
    }
}
