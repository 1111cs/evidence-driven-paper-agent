package org.example.paperaiagent.writing.infrastructure.persistence;

import org.example.paperaiagent.writing.citation.CitationValidationStatus;
import org.example.paperaiagent.writing.citation.SectionClaim;
import org.example.paperaiagent.writing.citation.SectionClaimId;
import org.example.paperaiagent.writing.citation.SectionClaimRepository;
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
public class JdbcSectionClaimRepository implements SectionClaimRepository {
    private final JdbcTemplate jdbc;

    public JdbcSectionClaimRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SectionClaim> saveAll(List<SectionClaim> claims) {
        for (SectionClaim claim : claims) {
            jdbc.update("""
                    INSERT INTO section_claims
                        (claim_id, task_id, section_version_id, claim_key, claim_text,
                         start_offset, end_offset, validation_status, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, claim.id().value(), claim.taskId().value(), claim.sectionVersionId().value(),
                    claim.claimKey(), claim.claimText(), claim.startOffset(), claim.endOffset(),
                    claim.validationStatus().name(), Timestamp.from(claim.createdAt()));
        }
        return List.copyOf(claims);
    }

    @Override
    public List<SectionClaim> findBySectionVersionId(SectionVersionId sectionVersionId) {
        return jdbc.query("""
                SELECT * FROM section_claims WHERE section_version_id = ? ORDER BY start_offset
                """, this::map, sectionVersionId.value());
    }

    @Override
    public long countBySectionVersionId(SectionVersionId sectionVersionId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM section_claims WHERE section_version_id = ?",
                Long.class, sectionVersionId.value());
        return count == null ? 0 : count;
    }

    private SectionClaim map(ResultSet rs, int row) throws SQLException {
        return new SectionClaim(
                new SectionClaimId(rs.getObject("claim_id", java.util.UUID.class)),
                new WritingTaskId(rs.getObject("task_id", java.util.UUID.class)),
                new SectionVersionId(rs.getObject("section_version_id", java.util.UUID.class)),
                rs.getString("claim_key"), rs.getString("claim_text"),
                rs.getInt("start_offset"), rs.getInt("end_offset"),
                CitationValidationStatus.valueOf(rs.getString("validation_status")),
                rs.getTimestamp("created_at").toInstant());
    }
}
