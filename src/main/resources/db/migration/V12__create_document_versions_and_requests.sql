CREATE TABLE document_versions (
    document_version_id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES writing_tasks(task_id),
    outline_version_id UUID NOT NULL,
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    format VARCHAR(32) NOT NULL CHECK (format = 'MARKDOWN'),
    assembler_version VARCHAR(100) NOT NULL,
    content_snapshot TEXT NOT NULL CHECK (length(content_snapshot) > 0),
    content_hash CHAR(64) NOT NULL,
    source_fingerprint CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_document_version_number UNIQUE (task_id, version_number),
    CONSTRAINT uq_document_source UNIQUE (task_id, source_fingerprint),
    CONSTRAINT uq_document_id_task UNIQUE (document_version_id, task_id),
    CONSTRAINT uq_document_id_task_outline UNIQUE (document_version_id, task_id, outline_version_id),
    CONSTRAINT fk_document_outline_task FOREIGN KEY (outline_version_id, task_id)
        REFERENCES outline_versions(outline_version_id, task_id)
);

CREATE INDEX idx_document_versions_task
    ON document_versions(task_id, version_number);

CREATE TABLE document_assembly_requests (
    task_id UUID NOT NULL,
    request_id VARCHAR(255) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    document_version_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (task_id, request_id),
    CONSTRAINT fk_document_request_version FOREIGN KEY (document_version_id, task_id)
        REFERENCES document_versions(document_version_id, task_id)
);

CREATE INDEX idx_document_requests_version
    ON document_assembly_requests(document_version_id, created_at);
