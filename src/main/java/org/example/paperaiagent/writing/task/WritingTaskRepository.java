package org.example.paperaiagent.writing.task;

import java.util.Optional;

public interface WritingTaskRepository {

    WritingTask save(WritingTask task);

    Optional<WritingTask> findById(WritingTaskId taskId);

    default Optional<WritingTask> findByIdForUpdate(WritingTaskId taskId) {
        return findById(taskId);
    }
}
