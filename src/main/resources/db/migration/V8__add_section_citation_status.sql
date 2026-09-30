ALTER TABLE section_versions
    ADD COLUMN citation_schema_version INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN citation_validation_status VARCHAR(32) NOT NULL DEFAULT 'LEGACY_UNVALIDATED';

ALTER TABLE section_versions
    ADD CONSTRAINT ck_section_citation_schema_version
        CHECK (citation_schema_version >= 0),
    ADD CONSTRAINT ck_section_citation_validation_status
        CHECK (citation_validation_status IN ('STRUCTURE_VALIDATED', 'LEGACY_UNVALIDATED')),
    ADD CONSTRAINT ck_section_citation_schema_status
        CHECK (
            (citation_schema_version = 0 AND citation_validation_status = 'LEGACY_UNVALIDATED')
            OR
            (citation_schema_version = 1 AND citation_validation_status = 'STRUCTURE_VALIDATED')
        );

ALTER TABLE section_versions
    ALTER COLUMN citation_schema_version DROP DEFAULT,
    ALTER COLUMN citation_validation_status DROP DEFAULT;
