ALTER TABLE outline_versions
    ADD CONSTRAINT uq_outline_version_id_task UNIQUE (outline_version_id, task_id);

CREATE TABLE section_versions (
    section_version_id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES writing_tasks(task_id),
    outline_version_id UUID NOT NULL,
    outline_section_id UUID NOT NULL,
    generated_by_run_id UUID NOT NULL UNIQUE REFERENCES writing_task_runs(run_id),
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    lock_version BIGINT NOT NULL CHECK (lock_version >= 0),
    status VARCHAR(32) NOT NULL,
    section_title TEXT NOT NULL,
    content_snapshot TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    CONSTRAINT uq_section_version_number UNIQUE (outline_section_id, version_number),
    CONSTRAINT uq_section_version_id_task UNIQUE (section_version_id, task_id),
    CONSTRAINT fk_section_version_outline_task FOREIGN KEY (outline_version_id, task_id)
        REFERENCES outline_versions(outline_version_id, task_id),
    CONSTRAINT fk_section_version_section_outline FOREIGN KEY (outline_section_id, outline_version_id)
        REFERENCES outline_sections(outline_section_id, outline_version_id)
);

CREATE UNIQUE INDEX uq_section_confirmed
    ON section_versions(outline_section_id)
    WHERE status = 'CONFIRMED';

CREATE INDEX idx_section_versions_task
    ON section_versions(task_id, created_at);

CREATE INDEX idx_section_versions_section
    ON section_versions(outline_section_id, version_number);
