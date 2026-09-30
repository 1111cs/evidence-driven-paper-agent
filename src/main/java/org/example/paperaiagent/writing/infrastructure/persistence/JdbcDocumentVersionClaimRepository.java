package org.example.paperaiagent.writing.infrastructure.persistence;

import org.example.paperaiagent.writing.citation.SectionClaimId;
import org.example.paperaiagent.writing.document.*;
import org.example.paperaiagent.writing.section.SectionVersionId;
import org.example.paperaiagent.writing.task.WritingTaskId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@ConditionalOnProperty(name = "paper.writing.repository-type", havingValue = "jdbc")
public class JdbcDocumentVersionClaimRepository implements DocumentVersionClaimRepository {
    private final JdbcTemplate jdbc;
    public JdbcDocumentVersionClaimRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public List<DocumentVersionClaim> saveAll(List<DocumentVersionClaim> values) {
        for (DocumentVersionClaim value : values) jdbc.update("""
                INSERT INTO document_version_claims(document_version_id, task_id, section_version_id,
                    claim_id, display_number) VALUES (?, ?, ?, ?, ?)
                """, value.documentVersionId().value(), value.taskId().value(), value.sectionVersionId().value(),
                value.claimId().value(), value.displayNumber());
        return List.copyOf(values);
    }
    @Override public List<DocumentVersionClaim> findByDocumentVersionId(DocumentVersionId id) {
        return jdbc.query("SELECT * FROM document_version_claims WHERE document_version_id = ? ORDER BY display_number",
                (rs, row) -> new DocumentVersionClaim(
                        new DocumentVersionId(rs.getObject("document_version_id", java.util.UUID.class)),
                        new WritingTaskId(rs.getObject("task_id", java.util.UUID.class)),
                        new SectionVersionId(rs.getObject("section_version_id", java.util.UUID.class)),
                        new SectionClaimId(rs.getObject("claim_id", java.util.UUID.class)),
                        rs.getInt("display_number")), id.value());
    }
}
