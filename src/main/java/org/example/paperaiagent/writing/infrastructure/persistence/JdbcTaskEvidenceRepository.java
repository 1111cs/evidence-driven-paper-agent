package org.example.paperaiagent.writing.infrastructure.persistence;

import org.example.paperaiagent.writing.evidence.EvidenceSourceType;
import org.example.paperaiagent.writing.evidence.PageNumberingScheme;
import org.example.paperaiagent.writing.evidence.TaskEvidence;
import org.example.paperaiagent.writing.evidence.TaskEvidenceId;
import org.example.paperaiagent.writing.evidence.TaskEvidenceRepository;
import org.example.paperaiagent.writing.run.TaskRunId;
import org.example.paperaiagent.writing.task.WritingTaskId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "paper.writing.repository-type", havingValue = "jdbc")
public class JdbcTaskEvidenceRepository implements TaskEvidenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcTaskEvidenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<TaskEvidence> saveAll(Collection<TaskEvidence> evidence) {
        List<TaskEvidence> inserted = new ArrayList<>();
        for (TaskEvidence item : evidence) {
            int count = jdbcTemplate.update(
                    """
                    INSERT INTO writing_task_evidence
                        (evidence_id, task_id, adopted_run_id, source_type, document_id, document_name,
                         section_name, pages, page_numbering_scheme, page_display_text, content_snapshot,
                         content_hash, source_candidate_id, stable_key, score, adopted_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (stable_key) DO NOTHING
                    """,
                    item.id().value(), item.taskId().value(), item.adoptedRunId().value(), item.sourceType().name(),
                    item.documentId(), item.documentName(), item.section(), encodePages(item.sourcePages()),
                    item.pageNumberingScheme().name(), item.pageDisplayText(),
                    item.contentSnapshot(), item.contentHash(), item.sourceCandidateId(), item.stableKey(),
                    item.score(), Timestamp.from(item.adoptedAt())
            );
            if (count == 1) {
                inserted.add(item);
            }
        }
        return List.copyOf(inserted);
    }

    @Override
    public List<TaskEvidence> findByTaskId(WritingTaskId taskId) {
        return jdbcTemplate.query(
                "SELECT * FROM writing_task_evidence WHERE task_id = ? ORDER BY adopted_at",
                this::map,
                taskId.value()
        );
    }

    @Override
    public boolean existsByStableKey(String stableKey) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM writing_task_evidence WHERE stable_key = ?)",
                Boolean.class,
                stableKey
        );
        return Boolean.TRUE.equals(exists);
    }

    private TaskEvidence map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TaskEvidence(
                new TaskEvidenceId(resultSet.getObject("evidence_id", java.util.UUID.class)),
                new WritingTaskId(resultSet.getObject("task_id", java.util.UUID.class)),
                new TaskRunId(resultSet.getObject("adopted_run_id", java.util.UUID.class)),
                EvidenceSourceType.valueOf(resultSet.getString("source_type")),
                resultSet.getString("document_id"),
                resultSet.getString("document_name"),
                resultSet.getString("section_name"),
                decodePages(resultSet.getString("pages")),
                PageNumberingScheme.valueOf(resultSet.getString("page_numbering_scheme")),
                resultSet.getString("page_display_text"),
                resultSet.getString("content_snapshot"),
                resultSet.getString("content_hash"),
                resultSet.getString("source_candidate_id"),
                resultSet.getString("stable_key"),
                resultSet.getDouble("score"),
                resultSet.getTimestamp("adopted_at").toInstant()
        );
    }

    private static String encodePages(List<Integer> pages) {
        return pages.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    private static List<Integer> decodePages(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .map(Integer::valueOf)
                .toList();
    }
}
