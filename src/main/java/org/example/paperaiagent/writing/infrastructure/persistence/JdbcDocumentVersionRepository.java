package org.example.paperaiagent.writing.infrastructure.persistence;

import org.example.paperaiagent.writing.document.*;
import org.example.paperaiagent.writing.outline.OutlineVersionId;
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
public class JdbcDocumentVersionRepository implements DocumentVersionRepository {
    private final JdbcTemplate jdbc;
    public JdbcDocumentVersionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public DocumentVersion save(DocumentVersion value) {
        jdbc.update("""
                INSERT INTO document_versions(document_version_id, task_id, outline_version_id,
                    version_number, format, assembler_version, content_snapshot, content_hash,
                    source_fingerprint, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, value.id().value(), value.taskId().value(), value.outlineVersionId().value(),
                value.versionNumber(), value.format().name(), value.assemblerVersion(), value.contentSnapshot(),
                value.contentHash(), value.sourceFingerprint(), Timestamp.from(value.createdAt()));
        return value;
    }
    @Override public Optional<DocumentVersion> findById(DocumentVersionId id) {
        return jdbc.query("SELECT * FROM document_versions WHERE document_version_id = ?", this::map, id.value())
                .stream().findFirst();
    }
    @Override public List<DocumentVersion> findByTaskId(WritingTaskId taskId) {
        return jdbc.query("SELECT * FROM document_versions WHERE task_id = ? ORDER BY version_number",
                this::map, taskId.value());
    }
    @Override public Optional<DocumentVersion> findByTaskIdAndSourceFingerprint(WritingTaskId taskId, String fingerprint) {
        return jdbc.query("SELECT * FROM document_versions WHERE task_id = ? AND source_fingerprint = ?",
                this::map, taskId.value(), fingerprint).stream().findFirst();
    }
    @Override public int nextVersionNumber(WritingTaskId taskId) {
        Integer value = jdbc.queryForObject("SELECT COALESCE(MAX(version_number), 0) + 1 FROM document_versions WHERE task_id = ?",
                Integer.class, taskId.value());
        return value == null ? 1 : value;
    }
    private DocumentVersion map(ResultSet rs, int row) throws SQLException {
        return new DocumentVersion(new DocumentVersionId(rs.getObject("document_version_id", java.util.UUID.class)),
                new WritingTaskId(rs.getObject("task_id", java.util.UUID.class)),
                new OutlineVersionId(rs.getObject("outline_version_id", java.util.UUID.class)),
                rs.getInt("version_number"), DocumentFormat.valueOf(rs.getString("format")),
                rs.getString("assembler_version"), rs.getString("content_snapshot"),
                rs.getString("content_hash"), rs.getString("source_fingerprint"),
                rs.getTimestamp("created_at").toInstant());
    }
}
