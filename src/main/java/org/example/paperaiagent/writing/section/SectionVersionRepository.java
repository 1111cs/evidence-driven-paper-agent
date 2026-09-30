package org.example.paperaiagent.writing.section;

import org.example.paperaiagent.writing.outline.OutlineSectionId;
import org.example.paperaiagent.writing.outline.OutlineVersionId;
import org.example.paperaiagent.writing.run.TaskRunId;
import org.example.paperaiagent.writing.task.WritingTaskId;

import java.util.List;
import java.util.Optional;

public interface SectionVersionRepository {
    SectionVersion save(SectionVersion version);
    Optional<SectionVersion> findById(SectionVersionId id);
    Optional<SectionVersion> findByGeneratedRunId(TaskRunId runId);
    List<SectionVersion> findByTaskId(WritingTaskId taskId);
    List<SectionVersion> findByOutlineSectionId(OutlineSectionId sectionId);
    List<SectionVersion> findConfirmedByOutlineVersionId(OutlineVersionId outlineVersionId);
    Optional<SectionVersion> findConfirmedByOutlineSectionId(OutlineSectionId sectionId);
    int nextVersionNumber(OutlineSectionId sectionId);
}
