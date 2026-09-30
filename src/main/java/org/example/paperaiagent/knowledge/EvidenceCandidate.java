package org.example.paperaiagent.knowledge;

import java.util.List;
import java.util.Objects;

public record EvidenceCandidate(
        String chunkId,
        String documentId,
        String documentName,
        String section,
        List<Integer> pages,
        String content,
        double score
) {

    public EvidenceCandidate {
        chunkId = requireText(chunkId, "chunkId");
        documentId = requireText(documentId, "documentId");
        documentName = requireText(documentName, "documentName");
        section = section == null ? "" : section.trim();
        pages = pages == null ? List.of() : List.copyOf(pages);
        content = requireText(content, "content");
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("score must be finite");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
