package org.example.paperaiagent.writing.infrastructure.memory;

import org.example.paperaiagent.writing.citation.SectionClaim;
import org.example.paperaiagent.writing.citation.SectionClaimId;
import org.example.paperaiagent.writing.citation.SectionClaimRepository;
import org.example.paperaiagent.writing.section.SectionVersionId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
@ConditionalOnProperty(name = "paper.writing.repository-type", havingValue = "memory", matchIfMissing = true)
public class InMemorySectionClaimRepository implements SectionClaimRepository {
    private final ConcurrentMap<SectionClaimId, SectionClaim> claims = new ConcurrentHashMap<>();

    @Override
    public List<SectionClaim> saveAll(List<SectionClaim> values) {
        values.forEach(value -> claims.put(value.id(), value));
        return List.copyOf(values);
    }

    @Override
    public List<SectionClaim> findBySectionVersionId(SectionVersionId sectionVersionId) {
        return claims.values().stream()
                .filter(item -> item.sectionVersionId().equals(sectionVersionId))
                .sorted(Comparator.comparingInt(SectionClaim::startOffset))
                .toList();
    }

    @Override
    public long countBySectionVersionId(SectionVersionId sectionVersionId) {
        return findBySectionVersionId(sectionVersionId).size();
    }
}
