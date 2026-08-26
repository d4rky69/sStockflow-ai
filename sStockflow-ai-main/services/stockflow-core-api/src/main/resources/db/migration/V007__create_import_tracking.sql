CREATE TABLE import_job (
    import_job_id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    import_type VARCHAR(50) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_sha256 VARCHAR(64) NOT NULL,
    import_mode VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    total_rows BIGINT NOT NULL DEFAULT 0,
    accepted_rows BIGINT NOT NULL DEFAULT 0,
    rejected_rows BIGINT NOT NULL DEFAULT 0,
    ignored_rows BIGINT NOT NULL DEFAULT 0,
    message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE import_error (
    import_error_id UUID PRIMARY KEY,
    import_job_id UUID NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    row_number BIGINT NOT NULL,
    error_code VARCHAR(80) NOT NULL,
    field_name VARCHAR(120),
    rejected_value VARCHAR(500),
    message VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_import_error_job
        FOREIGN KEY (import_job_id) REFERENCES import_job(import_job_id)
);

CREATE INDEX idx_import_job_tenant_started ON import_job(tenant_id, started_at);
CREATE INDEX idx_import_error_job ON import_error(import_job_id);
