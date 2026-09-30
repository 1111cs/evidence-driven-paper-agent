CREATE TABLE document_version_sections (
    document_version_id UUID NOT NULL,
    task_id UUID NOT NULL,
    outline_version_id UUID NOT NULL,
    outline_section_id UUID NOT NULL,
    section_version_id UUID NOT NULL,
    sequence_number INTEGER NOT NULL CHECK (sequence_number > 0),
    PRIMARY KEY (document_version_id, outline_section_id),
    CONSTRAINT uq_document_section_version UNIQUE (document_version_id, section_version_id),
    CONSTRAINT uq_document_section_sequence UNIQUE (document_version_id, sequence_number),
    CONSTRAINT uq_document_section_identity UNIQUE (document_version_id, section_version_id, task_id),
    CONSTRAINT fk_document_section_document
        FOREIGN KEY (document_version_id, task_id, outline_version_id)
        REFERENCES document_versions(document_version_id, task_id, outline_version_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_document_section_source
        FOREIGN KEY (section_version_id, outline_section_id, outline_version_id, task_id)
        REFERENCES section_versions(section_version_id, outline_section_id, outline_version_id, task_id)
);

CREATE INDEX idx_document_sections_order
    ON document_version_sections(document_version_id, sequence_number);
