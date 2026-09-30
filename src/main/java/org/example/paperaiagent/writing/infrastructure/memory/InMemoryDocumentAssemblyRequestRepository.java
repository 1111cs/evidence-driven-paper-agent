package org.example.paperaiagent.writing.infrastructure.memory;

import org.example.paperaiagent.writing.document.DocumentAssemblyRequest;
import org.example.paperaiagent.writing.document.DocumentAssemblyRequestRepository;
import org.example.paperaiagent.writing.document.DocumentVersionId;
import org.example.paperaiagent.writing.task.WritingTaskId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
@ConditionalOnProperty(name = "paper.writing.repository-type", havingValue = "memory", matchIfMissing = true)
public class InMemoryDocumentAssemblyRequestRepository implements DocumentAssemblyRequestRepository {
    private final ConcurrentMap<String, DocumentAssemblyRequest> values = new ConcurrentHashMap<>();
    private static String key(WritingTaskId taskId, String requestId) { return taskId + "\u0000" + requestId; }
    @Override public DocumentAssemblyRequest save(DocumentAssemblyRequest request) {
        DocumentAssemblyRequest existing = values.putIfAbsent(key(request.taskId(), request.requestId()), request);
        if (existing != null && !existing.equals(request)) throw new IllegalStateException("Duplicate document request");
        return existing == null ? request : existing;
    }
    @Override public Optional<DocumentAssemblyRequest> findByTaskIdAndRequestId(WritingTaskId taskId, String requestId) {
        return Optional.ofNullable(values.get(key(taskId, requestId)));
    }
    @Override public List<DocumentAssemblyRequest> findByDocumentVersionId(DocumentVersionId documentVersionId) {
        return values.values().stream().filter(item -> item.documentVersionId().equals(documentVersionId))
                .sorted(java.util.Comparator.comparing(DocumentAssemblyRequest::createdAt)).toList();
    }
}
