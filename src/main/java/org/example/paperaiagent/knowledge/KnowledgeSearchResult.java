package org.example.paperaiagent.knowledge;

import java.util.List;
import java.util.Objects;

public record KnowledgeSearchResult(
        String query,
        Status status,
        int total,
        List<EvidenceCandidate> candidates
) {

    public KnowledgeSearchResult {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(status, "status");
        if (total < 0) {
            throw new IllegalArgumentException("total must not be negative");
        }
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        if (status == Status.NO_RESULTS && !candidates.isEmpty()) {
            throw new IllegalArgumentException("NO_RESULTS must not contain candidates");
        }
    }

    public static KnowledgeSearchResult of(String query, int total, List<EvidenceCandidate> candidates) {
        List<EvidenceCandidate> safeCandidates = candidates == null ? List.of() : List.copyOf(candidates);
        Status status = safeCandidates.isEmpty() ? Status.NO_RESULTS : Status.RESULTS;
        return new KnowledgeSearchResult(query, status, total, safeCandidates);
    }

    public enum Status {
        RESULTS,
        NO_RESULTS
    }
}
