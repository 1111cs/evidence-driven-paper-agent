package org.example.paperaiagent.writing.outline;

import java.util.Objects;

public record OutlineSection(
        OutlineSectionId id,
        OutlineVersionId outlineVersionId,
        String sectionKey,
        String parentSectionKey,
        int sequenceNumber,
        int depth,
        String title,
        String objective,
        boolean writable
) {
    public OutlineSection {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(outlineVersionId, "outlineVersionId");
        sectionKey = requireText(sectionKey, "sectionKey");
        parentSectionKey = normalize(parentSectionKey);
        if (sequenceNumber < 1) throw new IllegalArgumentException("sequenceNumber must be positive");
        if (depth < 1 || depth > 3) throw new IllegalArgumentException("depth must be between 1 and 3");
        title = requireText(title, "title");
        objective = normalize(objective);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
