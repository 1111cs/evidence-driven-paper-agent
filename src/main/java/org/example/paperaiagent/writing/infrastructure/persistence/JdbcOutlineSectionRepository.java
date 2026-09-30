package org.example.paperaiagent.writing.infrastructure.persistence;

import org.example.paperaiagent.writing.outline.OutlineSection;
import org.example.paperaiagent.writing.outline.OutlineSectionId;
import org.example.paperaiagent.writing.outline.OutlineSectionRepository;
import org.example.paperaiagent.writing.outline.OutlineVersionId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "paper.writing.repository-type", havingValue = "jdbc")
public class JdbcOutlineSectionRepository implements OutlineSectionRepository {
    private final JdbcTemplate jdbc;

    public JdbcOutlineSectionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<OutlineSection> saveAll(Collection<OutlineSection> sections) {
        for (OutlineSection section : sections) {
            jdbc.update("""
                    INSERT INTO outline_sections
                        (outline_section_id, outline_version_id, section_key, parent_section_key,
                         sequence_number, depth, title, objective, writable)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (outline_version_id, section_key) DO NOTHING
                    """, section.id().value(), section.outlineVersionId().value(), section.sectionKey(),
                    section.parentSectionKey(), section.sequenceNumber(), section.depth(), section.title(),
                    section.objective(), section.writable());
        }
        if (sections.isEmpty()) return List.of();
        OutlineVersionId outlineId = sections.iterator().next().outlineVersionId();
        return findByOutlineVersionId(outlineId);
    }

    @Override
    public List<OutlineSection> findByOutlineVersionId(OutlineVersionId outlineVersionId) {
        return jdbc.query("""
                SELECT * FROM outline_sections WHERE outline_version_id = ? ORDER BY sequence_number
                """, (rs, row) -> new OutlineSection(
                new OutlineSectionId(rs.getObject("outline_section_id", java.util.UUID.class)),
                new OutlineVersionId(rs.getObject("outline_version_id", java.util.UUID.class)),
                rs.getString("section_key"), rs.getString("parent_section_key"),
                rs.getInt("sequence_number"), rs.getInt("depth"), rs.getString("title"),
                rs.getString("objective"), rs.getBoolean("writable")), outlineVersionId.value());
    }

    @Override
    public Optional<OutlineSection> findByOutlineVersionIdAndSectionKey(
            OutlineVersionId outlineVersionId, String sectionKey) {
        return jdbc.query("""
                SELECT * FROM outline_sections WHERE outline_version_id = ? AND section_key = ?
                """, (rs, row) -> new OutlineSection(
                new OutlineSectionId(rs.getObject("outline_section_id", java.util.UUID.class)),
                new OutlineVersionId(rs.getObject("outline_version_id", java.util.UUID.class)),
                rs.getString("section_key"), rs.getString("parent_section_key"),
                rs.getInt("sequence_number"), rs.getInt("depth"), rs.getString("title"),
                rs.getString("objective"), rs.getBoolean("writable")),
                outlineVersionId.value(), sectionKey).stream().findFirst();
    }

    @Override
    public boolean existsByOutlineVersionId(OutlineVersionId outlineVersionId) {
        Boolean value = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM outline_sections WHERE outline_version_id = ?)",
                Boolean.class, outlineVersionId.value());
        return Boolean.TRUE.equals(value);
    }
}
