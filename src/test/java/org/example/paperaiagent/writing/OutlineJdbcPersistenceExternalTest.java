package org.example.paperaiagent.writing;

import org.example.paperaiagent.writing.application.WritingHashes;
import org.example.paperaiagent.writing.evidence.EvidenceSourceType;
import org.example.paperaiagent.writing.evidence.PageNumberingScheme;
import org.example.paperaiagent.writing.evidence.TaskEvidence;
import org.example.paperaiagent.writing.evidence.TaskEvidenceId;
import org.example.paperaiagent.writing.infrastructure.persistence.JdbcOutlineVersionRepository;
import org.example.paperaiagent.writing.infrastructure.persistence.JdbcTaskEvidenceRepository;
import org.example.paperaiagent.writing.infrastructure.persistence.JdbcTaskRunRepository;
import org.example.paperaiagent.writing.infrastructure.persistence.JdbcWritingTaskRepository;
import org.example.paperaiagent.writing.outline.OutlineEvidenceReference;
import org.example.paperaiagent.writing.outline.OutlineStatus;
import org.example.paperaiagent.writing.outline.OutlineVersion;
import org.example.paperaiagent.writing.run.TaskRun;
import org.example.paperaiagent.writing.run.TaskRunAction;
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
class OutlineJdbcPersistenceExternalTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void rehydratesConfirmedOutlineAndEvidenceOrderFromJdbc() {
        var tasks = new JdbcWritingTaskRepository(jdbc);
        var runs = new JdbcTaskRunRepository(jdbc);
        var evidenceRepository = new JdbcTaskEvidenceRepository(jdbc);
        var outlines = new JdbcOutlineVersionRepository(jdbc);
        WritingTask task = WritingTask.create("Outline JDBC", "ALMT", "trace evidence");
        TaskRun research = null;
        TaskRun outlineRun = null;
        OutlineVersion outline = null;
        try {
            tasks.save(task);
            task.startResearch();
            tasks.save(task);

            research = TaskRun.create(task.id(), TaskRunAction.RESEARCH, "research-jdbc",
                    WritingHashes.sha256("research"), task.id().toString(), "research");
            research.start();
            runs.save(research);
            TaskEvidence evidence = new TaskEvidence(
                    TaskEvidenceId.newId(), task.id(), research.id(), EvidenceSourceType.CLOUD_KNOWLEDGE,
                    "doc-almt", "（4）ALMT", "3 Method", List.of(0, 1),
                    PageNumberingScheme.BAILIAN_RAW, "", "snapshot", WritingHashes.sha256("snapshot"),
                    "chunk-1", WritingHashes.sha256(task.id() + "chunk-1"), 0.9, Instant.now());
            evidenceRepository.saveAll(List.of(evidence));
            research.succeed("done", 1);
            runs.save(research);

            outlineRun = TaskRun.create(task.id(), TaskRunAction.GENERATE_OUTLINE, "outline-jdbc",
                    WritingHashes.sha256("outline"), task.id() + "-outline", "outline prompt");
            outlineRun.start();
            runs.save(outlineRun);
            outline = OutlineVersion.draft(task.id(), outlineRun.id(), 1, "Outline v1", "# Outline",
                    WritingHashes.sha256("# Outline"), List.of(new OutlineEvidenceReference(evidence.id(), 1)));
            outlines.save(outline);
            outlineRun.succeed("# Outline", 0);
            runs.save(outlineRun);
            task.markOutlinePending();
            tasks.save(task);
            outline.confirm();
            outlines.save(outline);
            task.confirmOutline(outline.id());
            tasks.save(task);

            var restartedTasks = new JdbcWritingTaskRepository(jdbc);
            var restartedOutlines = new JdbcOutlineVersionRepository(jdbc);
            var loadedTask = restartedTasks.findById(task.id()).orElseThrow();
            var loadedOutline = restartedOutlines.findById(outline.id()).orElseThrow();

            assertEquals(WritingStage.OUTLINE_CONFIRMED, loadedTask.stage());
            assertEquals(outline.id(), loadedTask.confirmedOutlineVersionId());
            assertEquals(OutlineStatus.CONFIRMED, loadedOutline.status());
            assertEquals(List.of(evidence.id()), loadedOutline.evidenceReferences().stream()
                    .map(OutlineEvidenceReference::evidenceId).toList());
        } finally {
            jdbc.update("UPDATE writing_tasks SET confirmed_outline_version_id = NULL WHERE task_id = ?", task.id().value());
            if (outline != null) {
                jdbc.update("DELETE FROM outline_version_evidence WHERE outline_version_id = ?", outline.id().value());
                jdbc.update("DELETE FROM outline_versions WHERE outline_version_id = ?", outline.id().value());
            }
            jdbc.update("DELETE FROM writing_task_evidence WHERE task_id = ?", task.id().value());
            jdbc.update("DELETE FROM writing_task_runs WHERE task_id = ?", task.id().value());
            jdbc.update("DELETE FROM writing_tasks WHERE task_id = ?", task.id().value());
        }
    }
}
