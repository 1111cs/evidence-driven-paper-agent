CREATE TABLE outline_sections (
    outline_section_id UUID PRIMARY KEY,
    outline_version_id UUID NOT NULL REFERENCES outline_versions(outline_version_id) ON DELETE CASCADE,
    section_key VARCHAR(100) NOT NULL,
    parent_section_key VARCHAR(100),
    sequence_number INTEGER NOT NULL CHECK (sequence_number > 0),
    depth INTEGER NOT NULL CHECK (depth BETWEEN 1 AND 3),
    title TEXT NOT NULL,
    objective TEXT,
    writable BOOLEAN NOT NULL,
    CONSTRAINT uq_outline_section_key UNIQUE (outline_version_id, section_key),
    CONSTRAINT uq_outline_section_sequence UNIQUE (outline_version_id, sequence_number),
    CONSTRAINT uq_outline_section_id_version UNIQUE (outline_section_id, outline_version_id),
    CONSTRAINT fk_outline_section_parent FOREIGN KEY (outline_version_id, parent_section_key)
        REFERENCES outline_sections(outline_version_id, section_key)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX idx_outline_sections_version
    ON outline_sections(outline_version_id, sequence_number);
