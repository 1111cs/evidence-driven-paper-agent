package org.example.paperaiagent.writing.infrastructure.persistence;

import org.example.paperaiagent.writing.document.*;
import org.example.paperaiagent.writing.outline.OutlineSectionId;
import org.example.paperaiagent.writing.outline.OutlineVersionId;
import org.example.paperaiagent.writing.section.SectionVersionId;
import org.example.paperaiagent.writing.task.WritingTaskId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@ConditionalOnProperty(name = "paper.writing.repository-type", havingValue = "jdbc")
public class JdbcDocumentVersionSectionRepository implements DocumentVersionSectionRepository {
    private final JdbcTemplate jdbc;
    public JdbcDocumentVersionSectionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public List<DocumentVersionSection> saveAll(List<DocumentVersionSection> values) {
        for (DocumentVersionSection value : values) jdbc.update("""
                INSERT INTO document_version_sections(document_version_id, task_id, outline_version_id,
                    outline_section_id, section_version_id, sequence_number) VALUES (?, ?, ?, ?, ?, ?)
                """, value.documentVersionId().value(), value.taskId().value(), value.outlineVersionId().value(),
                value.outlineSectionId().value(), value.sectionVersionId().value(), value.sequenceNumber());
        return List.copyOf(values);
    }
    @Override public List<DocumentVersionSection> findByDocumentVersionId(DocumentVersionId id) {
        return jdbc.query("SELECT * FROM document_version_sections WHERE document_version_id = ? ORDER BY sequence_number",
                (rs, row) -> new DocumentVersionSection(
                        new DocumentVersionId(rs.getObject("document_version_id", java.util.UUID.class)),
                        new WritingTaskId(rs.getObject("task_id", java.util.UUID.class)),
                        new OutlineVersionId(rs.getObject("outline_version_id", java.util.UUID.class)),
                        new OutlineSectionId(rs.getObject("outline_section_id", java.util.UUID.class)),
                        new SectionVersionId(rs.getObject("section_version_id", java.util.UUID.class)),
                        rs.getInt("sequence_number")), id.value());
    }
}
