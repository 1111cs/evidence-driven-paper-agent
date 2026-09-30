package org.example.paperaiagent.writing.outline;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OutlineSectionRepository {
    List<OutlineSection> saveAll(Collection<OutlineSection> sections);
    List<OutlineSection> findByOutlineVersionId(OutlineVersionId outlineVersionId);
    Optional<OutlineSection> findByOutlineVersionIdAndSectionKey(
            OutlineVersionId outlineVersionId, String sectionKey);
    boolean existsByOutlineVersionId(OutlineVersionId outlineVersionId);
}
