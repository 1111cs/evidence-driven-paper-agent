package org.example.paperaiagent.writing.infrastructure.persistence;

import org.example.paperaiagent.writing.task.WritingStage;
import org.example.paperaiagent.writing.outline.OutlineVersionId;
import org.example.paperaiagent.writing.task.WritingTask;
import org.example.paperaiagent.writing.task.WritingTaskId;
import org.example.paperaiagent.writing.task.WritingTaskRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "paper.writing.repository-type", havingValue = "jdbc")
public class JdbcWritingTaskRepository implements WritingTaskRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWritingTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public WritingTask save(WritingTask task) {
        if (task.version() == 0) {
            jdbcTemplate.update(
                    """
                    INSERT INTO writing_tasks
                        (task_id, title, topic, requirements, stage, confirmed_outline_version_id,
                         created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    task.id().value(), task.title(), task.topic(), task.requirements(), task.stage().name(),
                    task.confirmedOutlineVersionId() == null ? null : task.confirmedOutlineVersionId().value(),
                    Timestamp.from(task.createdAt()), Timestamp.from(task.updatedAt()), task.version()
            );
            return task;
        }

        int updated = jdbcTemplate.update(
                """
                UPDATE writing_tasks
                SET title = ?, topic = ?, requirements = ?, stage = ?, confirmed_outline_version_id = ?,
                    updated_at = ?, version = ?
                WHERE task_id = ? AND version = ?
                """,
                task.title(), task.topic(), task.requirements(), task.stage().name(),
                task.confirmedOutlineVersionId() == null ? null : task.confirmedOutlineVersionId().value(),
                Timestamp.from(task.updatedAt()), task.version(), task.id().value(), task.version() - 1
        );
        if (updated != 1) {
            throw new OptimisticLockingFailureException("WritingTask version conflict: " + task.id());
        }
        return task;
    }

    @Override
    public Optional<WritingTask> findById(WritingTaskId taskId) {
        return jdbcTemplate.query(
                "SELECT * FROM writing_tasks WHERE task_id = ?",
                this::map,
                taskId.value()
        ).stream().findFirst();
    }

    @Override
    public Optional<WritingTask> findByIdForUpdate(WritingTaskId taskId) {
        return jdbcTemplate.query(
                "SELECT * FROM writing_tasks WHERE task_id = ? FOR UPDATE",
                this::map,
                taskId.value()
        ).stream().findFirst();
    }

    private WritingTask map(ResultSet resultSet, int rowNumber) throws SQLException {
        return WritingTask.restore(
                new WritingTaskId(resultSet.getObject("task_id", java.util.UUID.class)),
                resultSet.getString("title"),
                resultSet.getString("topic"),
                resultSet.getString("requirements"),
                WritingStage.valueOf(resultSet.getString("stage")),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getLong("version"),
                resultSet.getObject("confirmed_outline_version_id") == null ? null
                        : new OutlineVersionId(resultSet.getObject("confirmed_outline_version_id", java.util.UUID.class))
        );
    }
}
