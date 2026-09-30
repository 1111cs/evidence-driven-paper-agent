package org.example.paperaiagent.writing.document;

import org.example.paperaiagent.writing.task.WritingTaskId;

import java.util.List;
import java.util.Optional;

public interface DocumentAssemblyRequestRepository {
    DocumentAssemblyRequest save(DocumentAssemblyRequest request);
    Optional<DocumentAssemblyRequest> findByTaskIdAndRequestId(WritingTaskId taskId, String requestId);
    List<DocumentAssemblyRequest> findByDocumentVersionId(DocumentVersionId documentVersionId);
}
