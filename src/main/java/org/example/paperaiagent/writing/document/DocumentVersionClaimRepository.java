package org.example.paperaiagent.writing.document;

import java.util.List;

public interface DocumentVersionClaimRepository {
    List<DocumentVersionClaim> saveAll(List<DocumentVersionClaim> claims);
    List<DocumentVersionClaim> findByDocumentVersionId(DocumentVersionId documentVersionId);
}
