package org.example.paperaiagent.writing.outline;

import org.example.paperaiagent.writing.run.TaskRunId;
import org.example.paperaiagent.writing.task.WritingTaskId;

import java.util.List;
import java.util.Optional;

public interface OutlineVersionRepository {
    OutlineVersion save(OutlineVersion outline);
    Optional<OutlineVersion> findById(OutlineVersionId id);
    Optional<OutlineVersion> findByGeneratedRunId(TaskRunId runId);
    List<OutlineVersion> findByTaskId(WritingTaskId taskId);
    int nextVersionNumber(WritingTaskId taskId);
}
