ALTER TABLE writing_tasks
    ADD COLUMN confirmed_outline_version_id UUID;

CREATE TABLE outline_versions (
    outline_version_id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES writing_tasks(task_id),
    generated_by_run_id UUID NOT NULL UNIQUE REFERENCES writing_task_runs(run_id),
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    lock_version BIGINT NOT NULL CHECK (lock_version >= 0),
    status VARCHAR(32) NOT NULL,
    title TEXT NOT NULL,
    content_snapshot TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    CONSTRAINT uq_outline_task_version UNIQUE (task_id, version_number)
);

CREATE TABLE outline_version_evidence (
    outline_version_id UUID NOT NULL REFERENCES outline_versions(outline_version_id) ON DELETE CASCADE,
    evidence_id UUID NOT NULL REFERENCES writing_task_evidence(evidence_id),
    sequence_number INTEGER NOT NULL CHECK (sequence_number > 0),
    PRIMARY KEY (outline_version_id, evidence_id),
    CONSTRAINT uq_outline_evidence_sequence UNIQUE (outline_version_id, sequence_number)
);

CREATE UNIQUE INDEX uq_outline_confirmed_per_task
    ON outline_versions(task_id)
    WHERE status = 'CONFIRMED';

CREATE INDEX idx_outline_task
    ON outline_versions(task_id, version_number);

ALTER TABLE writing_tasks
    ADD CONSTRAINT fk_writing_task_confirmed_outline
    FOREIGN KEY (confirmed_outline_version_id)
    REFERENCES outline_versions(outline_version_id);
