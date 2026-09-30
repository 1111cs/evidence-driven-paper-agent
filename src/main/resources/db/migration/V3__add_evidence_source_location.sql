ALTER TABLE writing_task_evidence
    ADD COLUMN page_numbering_scheme VARCHAR(32) NOT NULL DEFAULT 'BAILIAN_RAW';

ALTER TABLE writing_task_evidence
    ADD COLUMN page_display_text TEXT NOT NULL DEFAULT '';
