package org.example.paperaiagent.writing;

import org.example.paperaiagent.writing.evidence.EvidenceSourceType;
import org.example.paperaiagent.writing.evidence.PageNumberingScheme;
import org.example.paperaiagent.writing.evidence.TaskEvidence;
import org.example.paperaiagent.writing.evidence.TaskEvidenceId;
import org.example.paperaiagent.writing.infrastructure.persistence.JdbcTaskEvidenceRepository;
import org.example.paperaiagent.writing.infrastructure.persistence.JdbcTaskRunRepository;
import org.example.paperaiagent.writing.infrastructure.persistence.JdbcWritingTaskRepository;
import org.example.paperaiagent.writing.run.TaskRun;
import org.example.paperaiagent.writing.task.WritingTask;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("jdbc")
@SpringBootTest(properties = {
        "paper.writing.repository-type=jdbc",
        "paper.agent.runtime=agentscope",
        "paper.knowledge.enabled=false"
})
class JdbcWritingPersistenceExternalTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsTaskRunAndEvidenceAcrossRepositoryInstances() {
        JdbcWritingTaskRepository tasks = new JdbcWritingTaskRepository(jdbcTemplate);
        JdbcTaskRunRepository runs = new JdbcTaskRunRepository(jdbcTemplate);
        JdbcTaskEvidenceRepository evidence = new JdbcTaskEvidenceRepository(jdbcTemplate);

        WritingTask task = WritingTask.create("JDBC persistence test", "Persistence", "Keep evidence snapshots");
        TaskRun run = null;
        try {
            tasks.save(task);
            WritingTask loaded = tasks.findById(task.id()).orElseThrow();
            loaded.startResearch();
            tasks.save(loaded);

            run = TaskRun.create(loaded.id(), "jdbc-" + task.id(), loaded.id().toString(), "Research persistence");
            run.start();
            runs.save(run);
            TaskEvidence item = new TaskEvidence(
                    TaskEvidenceId.newId(),
                    loaded.id(),
                    run.id(),
                    EvidenceSourceType.CLOUD_KNOWLEDGE,
                    "doc-jdbc",
                    "JDBC document",
                    "1 Introduction",
                    List.of(1),
                    PageNumberingScheme.BAILIAN_RAW,
                    "",
                    "Persistent evidence snapshot",
                    "a".repeat(64),
                    "chunk-jdbc",
                    "b".repeat(64),
                    0.8,
                    Instant.now()
            );
            assertEquals(1, evidence.saveAll(List.of(item)).size());
            run.succeed("Persistent result", 1);
            runs.save(run);

            JdbcWritingTaskRepository restartedTasks = new JdbcWritingTaskRepository(jdbcTemplate);
            JdbcTaskRunRepository restartedRuns = new JdbcTaskRunRepository(jdbcTemplate);
            JdbcTaskEvidenceRepository restartedEvidence = new JdbcTaskEvidenceRepository(jdbcTemplate);

            assertEquals("JDBC persistence test", restartedTasks.findById(task.id()).orElseThrow().title());
            assertEquals("Persistent result", restartedRuns.findById(run.id()).orElseThrow().finalAnswer());
            assertEquals("Persistent evidence snapshot",
                    restartedEvidence.findByTaskId(task.id()).getFirst().contentSnapshot());
            assertTrue(restartedEvidence.existsByStableKey("b".repeat(64)));
        } finally {
            if (run != null) {
                jdbcTemplate.update("DELETE FROM writing_task_evidence WHERE adopted_run_id = ?", run.id().value());
                jdbcTemplate.update("DELETE FROM writing_task_runs WHERE run_id = ?", run.id().value());
            }
            jdbcTemplate.update("DELETE FROM writing_tasks WHERE task_id = ?", task.id().value());
        }
    }
}
