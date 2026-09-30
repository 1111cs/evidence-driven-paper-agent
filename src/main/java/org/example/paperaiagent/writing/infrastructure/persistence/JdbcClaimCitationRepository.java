package org.example.paperaiagent.writing.infrastructure.persistence;

import org.example.paperaiagent.writing.citation.ClaimCitation;
import org.example.paperaiagent.writing.citation.ClaimCitationId;
import org.example.paperaiagent.writing.citation.ClaimCitationRepository;
import org.example.paperaiagent.writing.citation.SectionClaimId;
import org.example.paperaiagent.writing.evidence.TaskEvidenceId;
import org.example.paperaiagent.writing.section.SectionVersionId;
import org.example.paperaiagent.writing.task.WritingTaskId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "paper.writing.repository-type", havingValue = "jdbc")
public class JdbcClaimCitationRepository implements ClaimCitationRepository {
    private final JdbcTemplate jdbc;

    public JdbcClaimCitationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ClaimCitation> saveAll(List<ClaimCitation> citations) {
        for (ClaimCitation citation : citations) {
            jdbc.update("""
                    INSERT INTO claim_citations
                        (citation_id, task_id, section_version_id, claim_id, evidence_id,
                         sequence_number, supporting_quote, evidence_start_offset,
                         evidence_end_offset, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, citation.id().value(), citation.taskId().value(),
                    citation.sectionVersionId().value(), citation.claimId().value(),
                    citation.evidenceId().value(), citation.sequenceNumber(), citation.supportingQuote(),
                    citation.evidenceStartOffset(), citation.evidenceEndOffset(),
                    Timestamp.from(citation.createdAt()));
        }
        return List.copyOf(citations);
    }

    @Override
    public List<ClaimCitation> findBySectionVersionId(SectionVersionId sectionVersionId) {
        return jdbc.query("""
                SELECT cc.*
                FROM claim_citations cc
                JOIN section_claims sc ON sc.claim_id = cc.claim_id
                WHERE cc.section_version_id = ?
                ORDER BY sc.start_offset, cc.sequence_number
                """, this::map, sectionVersionId.value());
    }

    @Override
    public long countBySectionVersionId(SectionVersionId sectionVersionId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM claim_citations WHERE section_version_id = ?",
                Long.class, sectionVersionId.value());
        return count == null ? 0 : count;
    }

    private ClaimCitation map(ResultSet rs, int row) throws SQLException {
        return new ClaimCitation(
                new ClaimCitationId(rs.getObject("citation_id", java.util.UUID.class)),
                new WritingTaskId(rs.getObject("task_id", java.util.UUID.class)),
                new SectionVersionId(rs.getObject("section_version_id", java.util.UUID.class)),
                new SectionClaimId(rs.getObject("claim_id", java.util.UUID.class)),
                new TaskEvidenceId(rs.getObject("evidence_id", java.util.UUID.class)),
                rs.getInt("sequence_number"), rs.getString("supporting_quote"),
                rs.getInt("evidence_start_offset"), rs.getInt("evidence_end_offset"),
                rs.getTimestamp("created_at").toInstant());
    }
}
