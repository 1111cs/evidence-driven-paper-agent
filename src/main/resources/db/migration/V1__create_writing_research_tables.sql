CREATE TABLE writing_tasks (
    task_id UUID PRIMARY KEY,
    title TEXT NOT NULL,
    topic TEXT NOT NULL,
    requirements TEXT NOT NULL,
    stage VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL CHECK (version >= 0)
);

CREATE TABLE writing_task_runs (
    run_id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES writing_tasks(task_id),
    action VARCHAR(32) NOT NULL DEFAULT 'RESEARCH',
    request_id VARCHAR(200) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    runtime_session_id VARCHAR(200) NOT NULL,
    research_prompt TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    final_answer TEXT,
    error_code VARCHAR(64),
    error_summary TEXT,
    adopted_evidence_count INTEGER NOT NULL DEFAULT 0 CHECK (adopted_evidence_count >= 0),
    CONSTRAINT uq_writing_run_action_request UNIQUE (task_id, action, request_id)
);

CREATE UNIQUE INDEX uq_writing_task_running_run
    ON writing_task_runs(task_id, action)
    WHERE status = 'RUNNING';

CREATE INDEX idx_writing_runs_task
    ON writing_task_runs(task_id, created_at);

CREATE TABLE writing_task_evidence (
    evidence_id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES writing_tasks(task_id),
    adopted_run_id UUID NOT NULL REFERENCES writing_task_runs(run_id),
    source_type VARCHAR(64) NOT NULL,
    document_id TEXT NOT NULL,
    document_name TEXT NOT NULL,
    section_name TEXT NOT NULL,
    pages TEXT NOT NULL,
    content_snapshot TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    source_candidate_id TEXT NOT NULL,
    stable_key CHAR(64) NOT NULL UNIQUE,
    score DOUBLE PRECISION NOT NULL,
    adopted_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_writing_evidence_task
    ON writing_task_evidence(task_id, adopted_at);
