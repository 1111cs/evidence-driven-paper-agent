package org.example.paperaiagent.writing.infrastructure.memory;

import org.example.paperaiagent.writing.evidence.TaskEvidence;
import org.example.paperaiagent.writing.evidence.TaskEvidenceId;
import org.example.paperaiagent.writing.evidence.TaskEvidenceRepository;
import org.example.paperaiagent.writing.task.WritingTaskId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
@ConditionalOnProperty(name = "paper.writing.repository-type", havingValue = "memory", matchIfMissing = true)
public class InMemoryTaskEvidenceRepository implements TaskEvidenceRepository {

    private final ConcurrentMap<TaskEvidenceId, TaskEvidence> evidenceById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TaskEvidenceId> idByStableKey = new ConcurrentHashMap<>();

    @Override
    public List<TaskEvidence> saveAll(Collection<TaskEvidence> evidence) {
        List<TaskEvidence> inserted = new ArrayList<>();
        for (TaskEvidence item : evidence) {
            TaskEvidenceId existing = idByStableKey.putIfAbsent(item.stableKey(), item.id());
            if (existing == null) {
                evidenceById.put(item.id(), item);
                inserted.add(item);
            }
        }
        return List.copyOf(inserted);
    }

    @Override
    public List<TaskEvidence> findByTaskId(WritingTaskId taskId) {
        return evidenceById.values().stream()
                .filter(item -> item.taskId().equals(taskId))
                .sorted(Comparator.comparing(TaskEvidence::adoptedAt))
                .toList();
    }

    @Override
    public boolean existsByStableKey(String stableKey) {
        return idByStableKey.containsKey(stableKey);
    }
}
