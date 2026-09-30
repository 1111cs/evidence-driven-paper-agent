package org.example.paperaiagent.writing.document;

import java.util.List;

public interface DocumentVersionSectionRepository {
    List<DocumentVersionSection> saveAll(List<DocumentVersionSection> sections);
    List<DocumentVersionSection> findByDocumentVersionId(DocumentVersionId documentVersionId);
}
