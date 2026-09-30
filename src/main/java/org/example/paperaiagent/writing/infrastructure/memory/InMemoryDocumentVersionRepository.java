package org.example.paperaiagent.writing.infrastructure.memory;

import org.example.paperaiagent.writing.document.DocumentVersion;
import org.example.paperaiagent.writing.document.DocumentVersionId;
import org.example.paperaiagent.writing.document.DocumentVersionRepository;
import org.example.paperaiagent.writing.task.WritingTaskId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
@ConditionalOnProperty(name = "paper.writing.repository-type", havingValue = "memory", matchIfMissing = true)
public class InMemoryDocumentVersionRepository implements DocumentVersionRepository {
    private final ConcurrentMap<DocumentVersionId, DocumentVersion> values = new ConcurrentHashMap<>();

    @Override public synchronized DocumentVersion save(DocumentVersion version) {
        findByTaskIdAndSourceFingerprint(version.taskId(), version.sourceFingerprint())
                .filter(item -> !item.id().equals(version.id()))
                .ifPresent(item -> { throw new IllegalStateException("Duplicate document source fingerprint"); });
        if (findByTaskId(version.taskId()).stream().anyMatch(item -> item.versionNumber() == version.versionNumber()
                && !item.id().equals(version.id()))) throw new IllegalStateException("Duplicate document version number");
        values.put(version.id(), version);
        return version;
    }
    @Override public Optional<DocumentVersion> findById(DocumentVersionId id) { return Optional.ofNullable(values.get(id)); }
    @Override public List<DocumentVersion> findByTaskId(WritingTaskId taskId) {
        return values.values().stream().filter(item -> item.taskId().equals(taskId))
                .sorted(Comparator.comparingInt(DocumentVersion::versionNumber)).toList();
    }
    @Override public Optional<DocumentVersion> findByTaskIdAndSourceFingerprint(WritingTaskId taskId, String fingerprint) {
        return values.values().stream().filter(item -> item.taskId().equals(taskId)
                && item.sourceFingerprint().equals(fingerprint)).findFirst();
    }
    @Override public int nextVersionNumber(WritingTaskId taskId) {
        return findByTaskId(taskId).stream().mapToInt(DocumentVersion::versionNumber).max().orElse(0) + 1;
    }
}
