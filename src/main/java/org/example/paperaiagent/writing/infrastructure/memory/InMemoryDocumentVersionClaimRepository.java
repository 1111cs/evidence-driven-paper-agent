package org.example.paperaiagent.writing.infrastructure.memory;

import org.example.paperaiagent.writing.document.DocumentVersionClaim;
import org.example.paperaiagent.writing.document.DocumentVersionClaimRepository;
import org.example.paperaiagent.writing.document.DocumentVersionId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "paper.writing.repository-type", havingValue = "memory", matchIfMissing = true)
public class InMemoryDocumentVersionClaimRepository implements DocumentVersionClaimRepository {
    private final List<DocumentVersionClaim> values = new ArrayList<>();
    @Override public synchronized List<DocumentVersionClaim> saveAll(List<DocumentVersionClaim> claims) {
        values.addAll(claims); return List.copyOf(claims);
    }
    @Override public synchronized List<DocumentVersionClaim> findByDocumentVersionId(DocumentVersionId id) {
        return values.stream().filter(item -> item.documentVersionId().equals(id))
                .sorted(Comparator.comparingInt(DocumentVersionClaim::displayNumber)).toList();
    }
}
