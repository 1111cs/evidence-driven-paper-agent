CREATE TABLE document_version_claims (
    document_version_id UUID NOT NULL,
    task_id UUID NOT NULL,
    section_version_id UUID NOT NULL,
    claim_id UUID NOT NULL,
    display_number INTEGER NOT NULL CHECK (display_number > 0),
    PRIMARY KEY (document_version_id, claim_id),
    CONSTRAINT uq_document_claim_display UNIQUE (document_version_id, display_number),
    CONSTRAINT fk_document_claim_section
        FOREIGN KEY (document_version_id, section_version_id, task_id)
        REFERENCES document_version_sections(document_version_id, section_version_id, task_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_document_claim_source
        FOREIGN KEY (claim_id, section_version_id, task_id)
        REFERENCES section_claims(claim_id, section_version_id, task_id)
);

CREATE INDEX idx_document_claims_display
    ON document_version_claims(document_version_id, display_number);
