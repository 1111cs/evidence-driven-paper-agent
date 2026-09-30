package org.example.paperaiagent.writing.run;

import org.example.paperaiagent.writing.task.WritingTaskId;

import java.util.List;
import java.util.Optional;

public interface TaskRunRepository {

    TaskRun save(TaskRun run);

    Optional<TaskRun> findById(TaskRunId runId);

    List<TaskRun> findByTaskId(WritingTaskId taskId);

    Optional<TaskRun> findByTaskIdActionAndRequestId(
            WritingTaskId taskId,
            TaskRunAction action,
            String requestId
    );

    boolean existsRunningByTaskIdAndAction(WritingTaskId taskId, TaskRunAction action);

    boolean existsRunningByTaskId(WritingTaskId taskId);

    default Optional<TaskRun> findByTaskIdAndRequestId(WritingTaskId taskId, String requestId) {
        return findByTaskIdActionAndRequestId(taskId, TaskRunAction.RESEARCH, requestId);
    }

}
