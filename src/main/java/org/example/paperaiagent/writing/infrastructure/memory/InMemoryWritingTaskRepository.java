package org.example.paperaiagent.writing.infrastructure.memory;

import org.example.paperaiagent.writing.task.WritingTask;
import org.example.paperaiagent.writing.task.WritingTaskId;
import org.example.paperaiagent.writing.task.WritingTaskRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
@ConditionalOnProperty(name = "paper.writing.repository-type", havingValue = "memory", matchIfMissing = true)
public class InMemoryWritingTaskRepository implements WritingTaskRepository {

    private final ConcurrentMap<WritingTaskId, WritingTask> tasks = new ConcurrentHashMap<>();

    @Override
    public WritingTask save(WritingTask task) {
        tasks.put(task.id(), task);
        return task;
    }

    @Override
    public Optional<WritingTask> findById(WritingTaskId taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }
}
