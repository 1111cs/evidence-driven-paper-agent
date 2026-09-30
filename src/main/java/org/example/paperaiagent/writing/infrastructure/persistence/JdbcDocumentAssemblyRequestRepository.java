package org.example.paperaiagent.writing.infrastructure.persistence;

import org.example.paperaiagent.writing.document.*;
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
public class JdbcDocumentAssemblyRequestRepository implements DocumentAssemblyRequestRepository {
    private final JdbcTemplate jdbc;
    public JdbcDocumentAssemblyRequestRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public DocumentAssemblyRequest save(DocumentAssemblyRequest value) {
        jdbc.update("""
                INSERT INTO document_assembly_requests(task_id, request_id, request_hash,
                    document_version_id, created_at) VALUES (?, ?, ?, ?, ?)
                """, value.taskId().value(), value.requestId(), value.requestHash(),
                value.documentVersionId().value(), Timestamp.from(value.createdAt()));
        return value;
    }
    @Override public Optional<DocumentAssemblyRequest> findByTaskIdAndRequestId(WritingTaskId taskId, String requestId) {
        return jdbc.query("SELECT * FROM document_assembly_requests WHERE task_id = ? AND request_id = ?",
                this::map, taskId.value(), requestId).stream().findFirst();
    }
    @Override public List<DocumentAssemblyRequest> findByDocumentVersionId(DocumentVersionId id) {
        return jdbc.query("SELECT * FROM document_assembly_requests WHERE document_version_id = ? ORDER BY created_at, request_id",
                this::map, id.value());
    }
    private DocumentAssemblyRequest map(ResultSet rs, int row) throws SQLException {
        return new DocumentAssemblyRequest(new WritingTaskId(rs.getObject("task_id", java.util.UUID.class)),
                rs.getString("request_id"), rs.getString("request_hash"),
                new DocumentVersionId(rs.getObject("document_version_id", java.util.UUID.class)),
                rs.getTimestamp("created_at").toInstant());
    }
}
