package org.example.paperaiagent.writing.infrastructure.memory;

import org.example.paperaiagent.writing.outline.OutlineVersion;
import org.example.paperaiagent.writing.outline.OutlineVersionId;
import org.example.paperaiagent.writing.outline.OutlineVersionRepository;
import org.example.paperaiagent.writing.run.TaskRunId;
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
public class InMemoryOutlineVersionRepository implements OutlineVersionRepository {
    private final ConcurrentMap<OutlineVersionId, OutlineVersion> outlines = new ConcurrentHashMap<>();

    @Override
    public OutlineVersion save(OutlineVersion outline) {
        outlines.put(outline.id(), outline);
        return outline;
    }

    @Override
    public Optional<OutlineVersion> findById(OutlineVersionId id) {
        return Optional.ofNullable(outlines.get(id));
    }

    @Override
    public Optional<OutlineVersion> findByGeneratedRunId(TaskRunId runId) {
        return outlines.values().stream().filter(item -> item.generatedByRunId().equals(runId)).findFirst();
    }

    @Override
    public List<OutlineVersion> findByTaskId(WritingTaskId taskId) {
        return outlines.values().stream()
                .filter(item -> item.taskId().equals(taskId))
                .sorted(Comparator.comparingInt(OutlineVersion::versionNumber))
                .toList();
    }

    @Override
    public int nextVersionNumber(WritingTaskId taskId) {
        return findByTaskId(taskId).stream().mapToInt(OutlineVersion::versionNumber).max().orElse(0) + 1;
    }
}
