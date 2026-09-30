package org.example.paperaiagent.writing.infrastructure.memory;

import org.example.paperaiagent.writing.outline.OutlineSection;
import org.example.paperaiagent.writing.outline.OutlineSectionId;
import org.example.paperaiagent.writing.outline.OutlineSectionRepository;
import org.example.paperaiagent.writing.outline.OutlineVersionId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
@ConditionalOnProperty(name = "paper.writing.repository-type", havingValue = "memory", matchIfMissing = true)
public class InMemoryOutlineSectionRepository implements OutlineSectionRepository {
    private final ConcurrentMap<OutlineSectionId, OutlineSection> sections = new ConcurrentHashMap<>();

    @Override
    public List<OutlineSection> saveAll(Collection<OutlineSection> items) {
        items.forEach(item -> sections.put(item.id(), item));
        return List.copyOf(items);
    }

    @Override
    public List<OutlineSection> findByOutlineVersionId(OutlineVersionId outlineVersionId) {
        return sections.values().stream()
                .filter(item -> item.outlineVersionId().equals(outlineVersionId))
                .sorted(Comparator.comparingInt(OutlineSection::sequenceNumber))
                .toList();
    }

    @Override
    public Optional<OutlineSection> findByOutlineVersionIdAndSectionKey(
            OutlineVersionId outlineVersionId, String sectionKey) {
        return findByOutlineVersionId(outlineVersionId).stream()
                .filter(item -> item.sectionKey().equals(sectionKey)).findFirst();
    }

    @Override
    public boolean existsByOutlineVersionId(OutlineVersionId outlineVersionId) {
        return sections.values().stream().anyMatch(item -> item.outlineVersionId().equals(outlineVersionId));
    }
}
