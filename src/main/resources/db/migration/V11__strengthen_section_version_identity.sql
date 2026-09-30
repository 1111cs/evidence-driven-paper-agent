ALTER TABLE section_versions
    ADD CONSTRAINT uq_section_version_full_identity UNIQUE
        (section_version_id, outline_section_id, outline_version_id, task_id);
