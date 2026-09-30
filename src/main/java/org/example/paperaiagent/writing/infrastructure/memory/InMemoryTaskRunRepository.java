package org.example.paperaiagent.writing.infrastructure.memory;

import org.example.paperaiagent.writing.run.TaskRun;
import org.example.paperaiagent.writing.run.TaskRunId;
import org.example.paperaiagent.writing.run.TaskRunAction;
import org.example.paperaiagent.writing.run.TaskRunRepository;
import org.example.paperaiagent.writing.run.TaskRunStatus;
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
public class InMemoryTaskRunRepository implements TaskRunRepository {

    private final ConcurrentMap<TaskRunId, TaskRun> runs = new ConcurrentHashMap<>();

    @Override
    public TaskRun save(TaskRun run) {
        runs.put(run.id(), run);
        return run;
    }

    @Override
    public Optional<TaskRun> findById(TaskRunId runId) {
        return Optional.ofNullable(runs.get(runId));
    }

    @Override
    public List<TaskRun> findByTaskId(WritingTaskId taskId) {
        return runs.values().stream()
                .filter(run -> run.taskId().equals(taskId))
                .sorted(Comparator.comparing(TaskRun::createdAt))
                .toList();
    }

    @Override
    public Optional<TaskRun> findByTaskIdActionAndRequestId(
            WritingTaskId taskId, TaskRunAction action, String requestId
    ) {
        return runs.values().stream()
                .filter(run -> run.taskId().equals(taskId)
                        && run.action() == action
                        && run.requestId().equals(requestId))
                .findFirst();
    }

    @Override
    public boolean existsRunningByTaskIdAndAction(WritingTaskId taskId, TaskRunAction action) {
        return runs.values().stream()
                .anyMatch(run -> run.taskId().equals(taskId)
                        && run.action() == action
                        && run.status() == TaskRunStatus.RUNNING);
    }

    @Override
    public boolean existsRunningByTaskId(WritingTaskId taskId) {
        return runs.values().stream()
                .anyMatch(run -> run.taskId().equals(taskId) && run.status() == TaskRunStatus.RUNNING);
    }
}
