package org.example.paperaiagent.writing.infrastructure.persistence;

import org.example.paperaiagent.writing.evidence.TaskEvidenceId;
import org.example.paperaiagent.writing.outline.OutlineEvidenceReference;
import org.example.paperaiagent.writing.outline.OutlineStatus;
import org.example.paperaiagent.writing.outline.OutlineVersion;
import org.example.paperaiagent.writing.outline.OutlineVersionId;
import org.example.paperaiagent.writing.outline.OutlineVersionRepository;
import org.example.paperaiagent.writing.run.TaskRunId;
import org.example.paperaiagent.writing.task.WritingTaskId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "paper.writing.repository-type", havingValue = "jdbc")
public class JdbcOutlineVersionRepository implements OutlineVersionRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcOutlineVersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public OutlineVersion save(OutlineVersion outline) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outline_versions WHERE outline_version_id = ?", Integer.class,
                outline.id().value());
        if (exists != null && exists > 0) {
            int updated = jdbcTemplate.update("""
                    UPDATE outline_versions SET status = ?, confirmed_at = ?, lock_version = ?
                    WHERE outline_version_id = ? AND lock_version = ?
                    """, outline.status().name(), timestamp(outline.confirmedAt()), outline.lockVersion(),
                    outline.id().value(), outline.lockVersion() - 1);
            if (updated != 1 && outline.lockVersion() > 0) {
                throw new OptimisticLockingFailureException("OutlineVersion conflict: " + outline.id());
            }
            return outline;
        }

        jdbcTemplate.update("""
                INSERT INTO outline_versions
                    (outline_version_id, task_id, generated_by_run_id, version_number, lock_version,
                     status, title, content_snapshot, content_hash, created_at, confirmed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, outline.id().value(), outline.taskId().value(), outline.generatedByRunId().value(),
                outline.versionNumber(), outline.lockVersion(), outline.status().name(), outline.title(),
                outline.contentSnapshot(), outline.contentHash(), Timestamp.from(outline.createdAt()),
                timestamp(outline.confirmedAt()));
        for (OutlineEvidenceReference reference : outline.evidenceReferences()) {
            jdbcTemplate.update("""
                    INSERT INTO outline_version_evidence(outline_version_id, evidence_id, sequence_number)
                    VALUES (?, ?, ?)
                    """, outline.id().value(), reference.evidenceId().value(), reference.sequenceNumber());
        }
        return outline;
    }

    @Override
    public Optional<OutlineVersion> findById(OutlineVersionId id) {
        return jdbcTemplate.query("SELECT * FROM outline_versions WHERE outline_version_id = ?",
                this::map, id.value()).stream().findFirst();
    }

    @Override
    public Optional<OutlineVersion> findByGeneratedRunId(TaskRunId runId) {
        return jdbcTemplate.query("SELECT * FROM outline_versions WHERE generated_by_run_id = ?",
                this::map, runId.value()).stream().findFirst();
    }

    @Override
    public List<OutlineVersion> findByTaskId(WritingTaskId taskId) {
        return jdbcTemplate.query("SELECT * FROM outline_versions WHERE task_id = ? ORDER BY version_number",
                this::map, taskId.value());
    }

    @Override
    public int nextVersionNumber(WritingTaskId taskId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version_number), 0) + 1 FROM outline_versions WHERE task_id = ?",
                Integer.class, taskId.value());
        return value == null ? 1 : value;
    }

    private OutlineVersion map(ResultSet rs, int row) throws SQLException {
        OutlineVersionId id = new OutlineVersionId(rs.getObject("outline_version_id", java.util.UUID.class));
        List<OutlineEvidenceReference> references = jdbcTemplate.query("""
                SELECT evidence_id, sequence_number FROM outline_version_evidence
                WHERE outline_version_id = ? ORDER BY sequence_number
                """, (nested, nestedRow) -> new OutlineEvidenceReference(
                        new TaskEvidenceId(nested.getObject("evidence_id", java.util.UUID.class)),
                        nested.getInt("sequence_number")), id.value());
        return OutlineVersion.restore(id,
                new WritingTaskId(rs.getObject("task_id", java.util.UUID.class)),
                new TaskRunId(rs.getObject("generated_by_run_id", java.util.UUID.class)),
                rs.getInt("version_number"), rs.getLong("lock_version"),
                OutlineStatus.valueOf(rs.getString("status")), rs.getString("title"),
                rs.getString("content_snapshot"), rs.getString("content_hash"), references,
                rs.getTimestamp("created_at").toInstant(), instant(rs.getTimestamp("confirmed_at")));
    }

    private static Timestamp timestamp(java.time.Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static java.time.Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
