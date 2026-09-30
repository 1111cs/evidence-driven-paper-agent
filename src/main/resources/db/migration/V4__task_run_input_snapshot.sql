ALTER TABLE writing_task_runs
    ADD COLUMN input_snapshot TEXT;

UPDATE writing_task_runs
SET input_snapshot = research_prompt
WHERE input_snapshot IS NULL;

ALTER TABLE writing_task_runs
    ALTER COLUMN input_snapshot SET NOT NULL;

DROP INDEX uq_writing_task_running_run;

CREATE UNIQUE INDEX uq_writing_task_running_run
    ON writing_task_runs(task_id)
    WHERE status = 'RUNNING';
