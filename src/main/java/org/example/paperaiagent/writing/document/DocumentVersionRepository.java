package org.example.paperaiagent.writing.document;

import org.example.paperaiagent.writing.task.WritingTaskId;

import java.util.List;
import java.util.Optional;

public interface DocumentVersionRepository {
    DocumentVersion save(DocumentVersion version);
    Optional<DocumentVersion> findById(DocumentVersionId id);
    List<DocumentVersion> findByTaskId(WritingTaskId taskId);
    Optional<DocumentVersion> findByTaskIdAndSourceFingerprint(WritingTaskId taskId, String sourceFingerprint);
    int nextVersionNumber(WritingTaskId taskId);
}
