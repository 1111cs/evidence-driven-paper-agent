package org.example.paperaiagent.writing;

import org.example.paperaiagent.writing.application.WritingHashes;
import org.example.paperaiagent.writing.evidence.EvidenceSourceType;
import org.example.paperaiagent.writing.evidence.PageNumberingScheme;
import org.example.paperaiagent.writing.evidence.TaskEvidence;
import org.example.paperaiagent.writing.evidence.TaskEvidenceId;
import org.example.paperaiagent.writing.infrastructure.persistence.JdbcOutlineSectionRepository;
import org.example.paperaiagent.writing.infrastructure.persistence.JdbcOutlineVersionRepository;
import org.example.paperaiagent.writing.infrastructure.persistence.JdbcSectionVersionRepository;
import org.example.paperaiagent.writing.infrastructure.persistence.JdbcTaskEvidenceRepository;
import org.example.paperaiagent.writing.infrastructure.persistence.JdbcTaskRunRepository;
import org.example.paperaiagent.writing.infrastructure.persistence.JdbcWritingTaskRepository;
import org.example.paperaiagent.writing.outline.OutlineEvidenceReference;
import org.example.paperaiagent.writing.outline.OutlineStructureExtractor;
import org.example.paperaiagent.writing.outline.OutlineVersion;
import org.example.paperaiagent.writing.run.TaskRun;
import org.example.paperaiagent.writing.run.TaskRunAction;
import org.example.paperaiagent.writing.section.SectionEvidenceReference;
import org.example.paperaiagent.writing.section.SectionVersion;
import org.example.paperaiagent.writing.section.SectionVersionStatus;
import org.example.paperaiagent.writing.task.WritingStage;
import org.example.paperaiagent.writing.task.WritingTask;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("jdbc")
@SpringBootTest(properties = {
        "paper.writing.repository-type=jdbc",
        "paper.agent.runtime=agentscope",
        "paper.knowledge.enabled=false"
})
class SectionJdbcPersistenceExternalTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void persistsStructureSectionVersionEvidenceAndConfirmationAcrossRepositoryInstances() {
        var tasks = new JdbcWritingTaskRepository(jdbc);
        var runs = new JdbcTaskRunRepository(jdbc);
        var evidenceRepository = new JdbcTaskEvidenceRepository(jdbc);
        var outlines = new JdbcOutlineVersionRepository(jdbc);
        var outlineSections = new JdbcOutlineSectionRepository(jdbc);
        var sections = new JdbcSectionVersionRepository(jdbc);

        WritingTask task = WritingTask.create("Section JDBC", "Section", "Use evidence");
        TaskRun researchRun = null;
        TaskRun outlineRun = null;
        TaskRun sectionRun = null;
        try {
            tasks.save(task);
            task.startResearch();
            tasks.save(task);
            researchRun = TaskRun.create(task.id(), TaskRunAction.RESEARCH, "research-" + task.id(),
                    "a".repeat(64), task.id().toString(), "research input");
            researchRun.start();
            runs.save(researchRun);
            TaskEvidence evidence = new TaskEvidence(TaskEvidenceId.newId(), task.id(), researchRun.id(),
                    EvidenceSourceType.CLOUD_KNOWLEDGE, "doc-section", "Section doc", "1 Introduction",
                    List.of(1), PageNumberingScheme.BAILIAN_RAW, "", "Evidence snapshot",
                    "b".repeat(64), "chunk-section", "c".repeat(64), 0.9, Instant.now());
            evidenceRepository.saveAll(List.of(evidence));
            researchRun.succeed("research complete", 1);
            runs.save(researchRun);

            outlineRun = TaskRun.create(task.id(), TaskRunAction.GENERATE_OUTLINE,
                    "outline-" + task.id(), "d".repeat(64), task.id() + "-outline", "outline input");
            outlineRun.start();
            runs.save(outlineRun);
            String outlineContent = "# Introduction\nExplain the research problem.";
            OutlineVersion outline = OutlineVersion.draft(task.id(), outlineRun.id(), 1, "Outline",
                    outlineContent, WritingHashes.sha256(outlineContent),
                    List.of(new OutlineEvidenceReference(evidence.id(), 1)));
            outlines.save(outline);
            outlineSections.saveAll(new OutlineStructureExtractor().extract(outline));
            outline.confirm();
            outlines.save(outline);
            outlineRun.succeed(outlineContent, 0);
            runs.save(outlineRun);
            task.markOutlinePending();
            tasks.save(task);
            task.confirmOutline(outline.id());
            tasks.save(task);

            var target = outlineSections.findByOutlineVersionId(outline.id()).getFirst();
            sectionRun = TaskRun.create(task.id(), TaskRunAction.GENERATE_SECTION,
                    "section-" + task.id(), "e".repeat(64), task.id() + "-section", "section input");
            sectionRun.start();
            runs.save(sectionRun);
            SectionVersion section = SectionVersion.citationValidatedDraft(task.id(), outline.id(), target.id(), sectionRun.id(),
                    1, target.title(), "Persistent section", WritingHashes.sha256("Persistent section"),
                    List.of(new SectionEvidenceReference(evidence.id(), 1)));
            sections.save(section);
            sectionRun.succeed(section.contentSnapshot(), 0);
            runs.save(sectionRun);
            task.startDrafting();
            tasks.save(task);
            section.confirm();
            sections.save(section);
            task.completeDraft();
            tasks.save(task);

            var restartedTasks = new JdbcWritingTaskRepository(jdbc);
            var restartedOutlineSections = new JdbcOutlineSectionRepository(jdbc);
            var restartedSections = new JdbcSectionVersionRepository(jdbc);
            assertEquals(WritingStage.DRAFT_COMPLETED,
                    restartedTasks.findById(task.id()).orElseThrow().stage());
            assertEquals(1, restartedOutlineSections.findByOutlineVersionId(outline.id()).size());
            SectionVersion restored = restartedSections.findById(section.id()).orElseThrow();
            assertEquals(SectionVersionStatus.CONFIRMED, restored.status());
            assertEquals("Persistent section", restored.contentSnapshot());
            assertEquals(List.of(evidence.id()), restored.evidenceReferences().stream()
                    .map(SectionEvidenceReference::evidenceId).toList());
        } finally {
            jdbc.update("DELETE FROM section_version_evidence WHERE task_id = ?", task.id().value());
            jdbc.update("DELETE FROM section_versions WHERE task_id = ?", task.id().value());
            jdbc.update("DELETE FROM outline_sections WHERE outline_version_id IN "
                    + "(SELECT outline_version_id FROM outline_versions WHERE task_id = ?)", task.id().value());
            jdbc.update("DELETE FROM outline_version_evidence WHERE outline_version_id IN "
                    + "(SELECT outline_version_id FROM outline_versions WHERE task_id = ?)", task.id().value());
            jdbc.update("UPDATE writing_tasks SET confirmed_outline_version_id = NULL WHERE task_id = ?",
                    task.id().value());
            jdbc.update("DELETE FROM outline_versions WHERE task_id = ?", task.id().value());
            jdbc.update("DELETE FROM writing_task_evidence WHERE task_id = ?", task.id().value());
            jdbc.update("DELETE FROM writing_task_runs WHERE task_id = ?", task.id().value());
            jdbc.update("DELETE FROM writing_tasks WHERE task_id = ?", task.id().value());
        }
    }
}
