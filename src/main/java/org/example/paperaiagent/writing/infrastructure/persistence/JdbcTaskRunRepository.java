package org.example.paperaiagent.writing.infrastructure.persistence;

import org.example.paperaiagent.writing.run.TaskRun;
import org.example.paperaiagent.writing.run.TaskRunAction;
import org.example.paperaiagent.writing.run.TaskRunId;
import org.example.paperaiagent.writing.run.TaskRunRepository;
import org.example.paperaiagent.writing.run.TaskRunStatus;
import org.example.paperaiagent.writing.task.WritingTaskId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "paper.writing.repository-type", havingValue = "jdbc")
public class JdbcTaskRunRepository implements TaskRunRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcTaskRunRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public TaskRun save(TaskRun run) {
        int updated = jdbcTemplate.update(
                """
                UPDATE writing_task_runs
                SET status = ?, started_at = ?, finished_at = ?, final_answer = ?, error_code = ?,
                    error_summary = ?, adopted_evidence_count = ?, input_snapshot = ?
                WHERE run_id = ?
                """,
                run.status().name(), timestamp(run.startedAt()), timestamp(run.finishedAt()), run.finalAnswer(),
                run.errorCode(), run.errorSummary(), run.adoptedEvidenceCount(), run.inputSnapshot(), run.id().value()
        );
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                    INSERT INTO writing_task_runs
                        (run_id, task_id, action, request_id, request_hash, runtime_session_id, research_prompt,
                         input_snapshot, status,
                         created_at, started_at, finished_at, final_answer, error_code, error_summary,
                         adopted_evidence_count)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    run.id().value(), run.taskId().value(), run.action().name(), run.requestId(), run.requestHash(), run.runtimeSessionId(),
                    run.inputSnapshot(), run.inputSnapshot(), run.status().name(), Timestamp.from(run.createdAt()),
                    timestamp(run.startedAt()), timestamp(run.finishedAt()), run.finalAnswer(), run.errorCode(),
                    run.errorSummary(), run.adoptedEvidenceCount()
            );
        }
        return run;
    }

    @Override
    public Optional<TaskRun> findById(TaskRunId runId) {
        return jdbcTemplate.query(
                "SELECT * FROM writing_task_runs WHERE run_id = ?",
                this::map,
                runId.value()
        ).stream().findFirst();
    }

    @Override
    public List<TaskRun> findByTaskId(WritingTaskId taskId) {
        return jdbcTemplate.query(
                "SELECT * FROM writing_task_runs WHERE task_id = ? ORDER BY created_at",
                this::map,
                taskId.value()
        );
    }

    @Override
    public Optional<TaskRun> findByTaskIdActionAndRequestId(
            WritingTaskId taskId, TaskRunAction action, String requestId
    ) {
        return jdbcTemplate.query(
                "SELECT * FROM writing_task_runs WHERE task_id = ? AND action = ? AND request_id = ?",
                this::map,
                taskId.value(), action.name(), requestId
        ).stream().findFirst();
    }

    @Override
    public boolean existsRunningByTaskIdAndAction(WritingTaskId taskId, TaskRunAction action) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM writing_task_runs WHERE task_id = ? AND action = ? AND status = 'RUNNING')",
                Boolean.class,
                taskId.value(), action.name()
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public boolean existsRunningByTaskId(WritingTaskId taskId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM writing_task_runs WHERE task_id = ? AND status = 'RUNNING')",
                Boolean.class,
                taskId.value()
        );
        return Boolean.TRUE.equals(exists);
    }

    private TaskRun map(ResultSet resultSet, int rowNumber) throws SQLException {
        return TaskRun.restore(
                new TaskRunId(resultSet.getObject("run_id", java.util.UUID.class)),
                new WritingTaskId(resultSet.getObject("task_id", java.util.UUID.class)),
                TaskRunAction.valueOf(resultSet.getString("action")),
                resultSet.getString("request_id"),
                resultSet.getString("request_hash"),
                resultSet.getString("runtime_session_id"),
                resultSet.getString("input_snapshot"),
                TaskRunStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toInstant(),
                instant(resultSet.getTimestamp("started_at")),
                instant(resultSet.getTimestamp("finished_at")),
                resultSet.getString("final_answer"),
                resultSet.getString("error_code"),
                resultSet.getString("error_summary"),
                resultSet.getInt("adopted_evidence_count")
        );
    }

    private static Timestamp timestamp(java.time.Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static java.time.Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
