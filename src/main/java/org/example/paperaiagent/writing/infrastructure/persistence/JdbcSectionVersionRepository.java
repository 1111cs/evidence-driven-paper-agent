package org.example.paperaiagent.writing.infrastructure.persistence;

import org.example.paperaiagent.writing.evidence.TaskEvidenceId;
import org.example.paperaiagent.writing.citation.CitationValidationStatus;
import org.example.paperaiagent.writing.outline.OutlineSectionId;
import org.example.paperaiagent.writing.outline.OutlineVersionId;
import org.example.paperaiagent.writing.run.TaskRunId;
import org.example.paperaiagent.writing.section.SectionEvidenceReference;
import org.example.paperaiagent.writing.section.SectionVersion;
import org.example.paperaiagent.writing.section.SectionVersionId;
import org.example.paperaiagent.writing.section.SectionVersionRepository;
import org.example.paperaiagent.writing.section.SectionVersionStatus;
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
public class JdbcSectionVersionRepository implements SectionVersionRepository {
    private final JdbcTemplate jdbc;

    public JdbcSectionVersionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SectionVersion save(SectionVersion version) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM section_versions WHERE section_version_id = ?",
                Integer.class, version.id().value());
        if (count != null && count > 0) {
            int updated = jdbc.update("""
                    UPDATE section_versions SET status = ?, confirmed_at = ?, lock_version = ?
                    WHERE section_version_id = ? AND lock_version = ?
                    """, version.status().name(), timestamp(version.confirmedAt()), version.lockVersion(),
                    version.id().value(), version.lockVersion() - 1);
            if (updated != 1 && version.lockVersion() > 0) {
                throw new OptimisticLockingFailureException("SectionVersion conflict: " + version.id());
            }
            return version;
        }
        jdbc.update("""
                INSERT INTO section_versions
                    (section_version_id, task_id, outline_version_id, outline_section_id,
                     generated_by_run_id, version_number, lock_version, status, section_title,
                     content_snapshot, content_hash, citation_schema_version,
                     citation_validation_status, created_at, confirmed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, version.id().value(), version.taskId().value(), version.outlineVersionId().value(),
                version.outlineSectionId().value(), version.generatedByRunId().value(), version.versionNumber(),
                version.lockVersion(), version.status().name(), version.sectionTitle(), version.contentSnapshot(),
                version.contentHash(), version.citationSchemaVersion(), version.citationValidationStatus().name(),
                Timestamp.from(version.createdAt()), timestamp(version.confirmedAt()));
        for (SectionEvidenceReference reference : version.evidenceReferences()) {
            jdbc.update("""
                    INSERT INTO section_version_evidence(task_id, section_version_id, evidence_id, sequence_number)
                    VALUES (?, ?, ?, ?)
                    """, version.taskId().value(), version.id().value(), reference.evidenceId().value(),
                    reference.sequenceNumber());
        }
        return version;
    }

    @Override
    public Optional<SectionVersion> findById(SectionVersionId id) {
        return jdbc.query("SELECT * FROM section_versions WHERE section_version_id = ?", this::map, id.value())
                .stream().findFirst();
    }

    @Override
    public Optional<SectionVersion> findByGeneratedRunId(TaskRunId runId) {
        return jdbc.query("SELECT * FROM section_versions WHERE generated_by_run_id = ?", this::map, runId.value())
                .stream().findFirst();
    }

    @Override
    public List<SectionVersion> findByTaskId(WritingTaskId taskId) {
        return jdbc.query("SELECT * FROM section_versions WHERE task_id = ? ORDER BY created_at",
                this::map, taskId.value());
    }

    @Override
    public List<SectionVersion> findByOutlineSectionId(OutlineSectionId sectionId) {
        return jdbc.query("SELECT * FROM section_versions WHERE outline_section_id = ? ORDER BY version_number",
                this::map, sectionId.value());
    }

    @Override
    public List<SectionVersion> findConfirmedByOutlineVersionId(OutlineVersionId outlineVersionId) {
        return jdbc.query("""
                SELECT * FROM section_versions
                WHERE outline_version_id = ? AND status = 'CONFIRMED' ORDER BY created_at
                """, this::map, outlineVersionId.value());
    }

    @Override
    public Optional<SectionVersion> findConfirmedByOutlineSectionId(OutlineSectionId sectionId) {
        return jdbc.query("""
                SELECT * FROM section_versions WHERE outline_section_id = ? AND status = 'CONFIRMED'
                """, this::map, sectionId.value()).stream().findFirst();
    }

    @Override
    public int nextVersionNumber(OutlineSectionId sectionId) {
        Integer value = jdbc.queryForObject("""
                SELECT COALESCE(MAX(version_number), 0) + 1 FROM section_versions WHERE outline_section_id = ?
                """, Integer.class, sectionId.value());
        return value == null ? 1 : value;
    }

    private SectionVersion map(ResultSet rs, int row) throws SQLException {
        SectionVersionId id = new SectionVersionId(rs.getObject("section_version_id", java.util.UUID.class));
        List<SectionEvidenceReference> references = jdbc.query("""
                SELECT evidence_id, sequence_number FROM section_version_evidence
                WHERE section_version_id = ? ORDER BY sequence_number
                """, (nested, nestedRow) -> new SectionEvidenceReference(
                new TaskEvidenceId(nested.getObject("evidence_id", java.util.UUID.class)),
                nested.getInt("sequence_number")), id.value());
        return SectionVersion.restore(id,
                new WritingTaskId(rs.getObject("task_id", java.util.UUID.class)),
                new OutlineVersionId(rs.getObject("outline_version_id", java.util.UUID.class)),
                new OutlineSectionId(rs.getObject("outline_section_id", java.util.UUID.class)),
                new TaskRunId(rs.getObject("generated_by_run_id", java.util.UUID.class)),
                rs.getInt("version_number"), rs.getLong("lock_version"),
                SectionVersionStatus.valueOf(rs.getString("status")), rs.getString("section_title"),
                rs.getString("content_snapshot"), rs.getString("content_hash"),
                rs.getInt("citation_schema_version"),
                CitationValidationStatus.valueOf(rs.getString("citation_validation_status")), references,
                rs.getTimestamp("created_at").toInstant(), instant(rs.getTimestamp("confirmed_at")));
    }

    private static Timestamp timestamp(java.time.Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static java.time.Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
