package org.example.paperaiagent.writing.evidence;

import org.example.paperaiagent.writing.task.WritingTaskId;

import java.util.Collection;
import java.util.List;

public interface TaskEvidenceRepository {

    List<TaskEvidence> saveAll(Collection<TaskEvidence> evidence);

    List<TaskEvidence> findByTaskId(WritingTaskId taskId);

    boolean existsByStableKey(String stableKey);
}
