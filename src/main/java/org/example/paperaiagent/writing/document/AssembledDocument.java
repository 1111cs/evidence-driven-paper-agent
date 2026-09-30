package org.example.paperaiagent.writing.document;

import java.util.List;

public record AssembledDocument(
        String contentSnapshot,
        String contentHash,
        String sourceFingerprint,
        List<DocumentVersionSectionDraft> sectionMappings,
        List<DocumentVersionClaimDraft> claimMappings,
        int outlineSectionCount,
        int writableSectionCount,
        int claimCount
) {
    public AssembledDocument {
        sectionMappings = List.copyOf(sectionMappings);
        claimMappings = List.copyOf(claimMappings);
    }
}
