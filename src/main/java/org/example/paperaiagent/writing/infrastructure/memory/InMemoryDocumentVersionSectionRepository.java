package org.example.paperaiagent.writing.infrastructure.memory;

import org.example.paperaiagent.writing.document.DocumentVersionId;
import org.example.paperaiagent.writing.document.DocumentVersionSection;
import org.example.paperaiagent.writing.document.DocumentVersionSectionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "paper.writing.repository-type", havingValue = "memory", matchIfMissing = true)
public class InMemoryDocumentVersionSectionRepository implements DocumentVersionSectionRepository {
    private final List<DocumentVersionSection> values = new ArrayList<>();
    @Override public synchronized List<DocumentVersionSection> saveAll(List<DocumentVersionSection> sections) {
        values.addAll(sections); return List.copyOf(sections);
    }
    @Override public synchronized List<DocumentVersionSection> findByDocumentVersionId(DocumentVersionId id) {
        return values.stream().filter(item -> item.documentVersionId().equals(id))
                .sorted(Comparator.comparingInt(DocumentVersionSection::sequenceNumber)).toList();
    }
}
