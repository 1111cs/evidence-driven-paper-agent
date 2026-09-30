CREATE TABLE claim_citations (
    citation_id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    section_version_id UUID NOT NULL,
    claim_id UUID NOT NULL,
    evidence_id UUID NOT NULL,
    sequence_number INTEGER NOT NULL CHECK (sequence_number > 0),
    supporting_quote TEXT NOT NULL CHECK (length(supporting_quote) > 0),
    evidence_start_offset INTEGER NOT NULL CHECK (evidence_start_offset >= 0),
    evidence_end_offset INTEGER NOT NULL CHECK (evidence_end_offset > evidence_start_offset),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_claim_citation_sequence UNIQUE (claim_id, sequence_number),
    CONSTRAINT uq_claim_citation_support UNIQUE
        (claim_id, evidence_id, evidence_start_offset, evidence_end_offset),
    CONSTRAINT fk_claim_citation_claim
        FOREIGN KEY (claim_id, section_version_id, task_id)
        REFERENCES section_claims(claim_id, section_version_id, task_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_claim_citation_context_evidence
        FOREIGN KEY (section_version_id, evidence_id)
        REFERENCES section_version_evidence(section_version_id, evidence_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_claim_citations_version
    ON claim_citations(section_version_id, claim_id, sequence_number);
