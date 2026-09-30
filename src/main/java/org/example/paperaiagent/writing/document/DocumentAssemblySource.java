package org.example.paperaiagent.writing.document;

import org.example.paperaiagent.writing.citation.ClaimCitation;
import org.example.paperaiagent.writing.citation.SectionClaim;
import org.example.paperaiagent.writing.citation.SectionClaimId;
import org.example.paperaiagent.writing.outline.OutlineSection;
import org.example.paperaiagent.writing.outline.OutlineSectionId;
import org.example.paperaiagent.writing.outline.OutlineVersion;
import org.example.paperaiagent.writing.section.SectionVersion;
import org.example.paperaiagent.writing.section.SectionVersionId;
import org.example.paperaiagent.writing.task.WritingTask;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record DocumentAssemblySource(
        WritingTask task,
        OutlineVersion outline,
        List<OutlineSection> outlineSections,
        Map<OutlineSectionId, SectionVersion> confirmedSections,
        Map<SectionVersionId, List<SectionClaim>> claims,
        Map<SectionClaimId, List<ClaimCitation>> citations
) {
    public DocumentAssemblySource {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(outline, "outline");
        outlineSections = List.copyOf(outlineSections);
        confirmedSections = Map.copyOf(confirmedSections);
        claims = copyLists(claims);
        citations = copyLists(citations);
    }

    private static <K, V> Map<K, List<V>> copyLists(Map<K, List<V>> source) {
        java.util.LinkedHashMap<K, List<V>> copy = new java.util.LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Map.copyOf(copy);
    }
}
