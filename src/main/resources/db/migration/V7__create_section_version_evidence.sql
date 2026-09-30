ALTER TABLE writing_task_evidence
    ADD CONSTRAINT uq_evidence_id_task UNIQUE (evidence_id, task_id);

CREATE TABLE section_version_evidence (
    task_id UUID NOT NULL,
    section_version_id UUID NOT NULL,
    evidence_id UUID NOT NULL,
    sequence_number INTEGER NOT NULL CHECK (sequence_number > 0),
    PRIMARY KEY (section_version_id, evidence_id),
    CONSTRAINT uq_section_evidence_sequence UNIQUE (section_version_id, sequence_number),
    CONSTRAINT fk_section_evidence_version_task FOREIGN KEY (section_version_id, task_id)
        REFERENCES section_versions(section_version_id, task_id) ON DELETE CASCADE,
    CONSTRAINT fk_section_evidence_evidence_task FOREIGN KEY (evidence_id, task_id)
        REFERENCES writing_task_evidence(evidence_id, task_id)
);

CREATE INDEX idx_section_evidence_version
    ON section_version_evidence(section_version_id, sequence_number);
