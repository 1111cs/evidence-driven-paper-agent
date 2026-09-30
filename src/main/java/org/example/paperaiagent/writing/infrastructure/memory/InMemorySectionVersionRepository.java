package org.example.paperaiagent.writing.infrastructure.memory;

import org.example.paperaiagent.writing.outline.OutlineSectionId;
import org.example.paperaiagent.writing.outline.OutlineVersionId;
import org.example.paperaiagent.writing.run.TaskRunId;
import org.example.paperaiagent.writing.section.SectionVersion;
import org.example.paperaiagent.writing.section.SectionVersionId;
import org.example.paperaiagent.writing.section.SectionVersionRepository;
import org.example.paperaiagent.writing.section.SectionVersionStatus;
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
public class InMemorySectionVersionRepository implements SectionVersionRepository {
    private final ConcurrentMap<SectionVersionId, SectionVersion> versions = new ConcurrentHashMap<>();

    @Override
    public SectionVersion save(SectionVersion version) {
        versions.put(version.id(), version);
        return version;
    }

    @Override
    public Optional<SectionVersion> findById(SectionVersionId id) {
        return Optional.ofNullable(versions.get(id));
    }

    @Override
    public Optional<SectionVersion> findByGeneratedRunId(TaskRunId runId) {
        return versions.values().stream().filter(item -> item.generatedByRunId().equals(runId)).findFirst();
    }

    @Override
    public List<SectionVersion> findByTaskId(WritingTaskId taskId) {
        return versions.values().stream().filter(item -> item.taskId().equals(taskId))
                .sorted(Comparator.comparing(SectionVersion::createdAt)).toList();
    }

    @Override
    public List<SectionVersion> findByOutlineSectionId(OutlineSectionId sectionId) {
        return versions.values().stream().filter(item -> item.outlineSectionId().equals(sectionId))
                .sorted(Comparator.comparingInt(SectionVersion::versionNumber)).toList();
    }

    @Override
    public List<SectionVersion> findConfirmedByOutlineVersionId(OutlineVersionId outlineVersionId) {
        return versions.values().stream()
                .filter(item -> item.outlineVersionId().equals(outlineVersionId)
                        && item.status() == SectionVersionStatus.CONFIRMED)
                .toList();
    }

    @Override
    public Optional<SectionVersion> findConfirmedByOutlineSectionId(OutlineSectionId sectionId) {
        return findByOutlineSectionId(sectionId).stream()
                .filter(item -> item.status() == SectionVersionStatus.CONFIRMED).findFirst();
    }

    @Override
    public int nextVersionNumber(OutlineSectionId sectionId) {
        return findByOutlineSectionId(sectionId).stream().mapToInt(SectionVersion::versionNumber).max().orElse(0) + 1;
    }
}
