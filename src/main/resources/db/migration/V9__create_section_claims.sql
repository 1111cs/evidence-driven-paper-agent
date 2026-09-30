CREATE TABLE section_claims (
    claim_id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    section_version_id UUID NOT NULL,
    claim_key VARCHAR(64) NOT NULL,
    claim_text TEXT NOT NULL,
    start_offset INTEGER NOT NULL CHECK (start_offset >= 0),
    end_offset INTEGER NOT NULL CHECK (end_offset > start_offset),
    validation_status VARCHAR(32) NOT NULL
        CHECK (validation_status = 'STRUCTURE_VALIDATED'),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_section_claim_key UNIQUE (section_version_id, claim_key),
    CONSTRAINT uq_section_claim_range UNIQUE (section_version_id, start_offset, end_offset),
    CONSTRAINT uq_section_claim_identity UNIQUE (claim_id, section_version_id, task_id),
    CONSTRAINT fk_section_claim_version_task
        FOREIGN KEY (section_version_id, task_id)
        REFERENCES section_versions(section_version_id, task_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_section_claims_version
    ON section_claims(section_version_id, start_offset);
