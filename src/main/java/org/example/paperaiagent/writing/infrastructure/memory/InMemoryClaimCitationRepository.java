package org.example.paperaiagent.writing.infrastructure.memory;

import org.example.paperaiagent.writing.citation.ClaimCitation;
import org.example.paperaiagent.writing.citation.ClaimCitationId;
import org.example.paperaiagent.writing.citation.ClaimCitationRepository;
import org.example.paperaiagent.writing.section.SectionVersionId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
@ConditionalOnProperty(name = "paper.writing.repository-type", havingValue = "memory", matchIfMissing = true)
public class InMemoryClaimCitationRepository implements ClaimCitationRepository {
    private final ConcurrentMap<ClaimCitationId, ClaimCitation> citations = new ConcurrentHashMap<>();

    @Override
    public List<ClaimCitation> saveAll(List<ClaimCitation> values) {
        values.forEach(value -> citations.put(value.id(), value));
        return List.copyOf(values);
    }

    @Override
    public List<ClaimCitation> findBySectionVersionId(SectionVersionId sectionVersionId) {
        return citations.values().stream()
                .filter(item -> item.sectionVersionId().equals(sectionVersionId))
                .sorted(Comparator.comparing((ClaimCitation item) -> item.claimId().toString())
                        .thenComparingInt(ClaimCitation::sequenceNumber))
                .toList();
    }

    @Override
    public long countBySectionVersionId(SectionVersionId sectionVersionId) {
        return findBySectionVersionId(sectionVersionId).size();
    }
}
