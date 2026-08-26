CREATE TABLE forecast_schedule (
    schedule_id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenant(tenant_id),
    schedule_name VARCHAR(160) NOT NULL,
    cadence VARCHAR(20) NOT NULL CHECK (cadence IN ('DAILY','WEEKLY')),
    day_of_week INTEGER CHECK (day_of_week BETWEEN 1 AND 7),
    run_hour INTEGER NOT NULL CHECK (run_hour BETWEEN 0 AND 23),
    run_minute INTEGER NOT NULL CHECK (run_minute BETWEEN 0 AND 59),
    timezone VARCHAR(80) NOT NULL DEFAULT 'Asia/Kolkata',
    horizon_days INTEGER NOT NULL CHECK (horizon_days IN (7,30,90)),
    history_days INTEGER CHECK (history_days BETWEEN 28 AND 365),
    warehouse_id VARCHAR(64) REFERENCES warehouse(warehouse_id),
    sku_id VARCHAR(80) REFERENCES sku(sku_id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    next_run_at TIMESTAMP NOT NULL,
    created_by VARCHAR(128) NOT NULL REFERENCES app_user(user_id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_forecast_schedule_name UNIQUE (tenant_id,schedule_name),
    CONSTRAINT ck_weekly_schedule_day CHECK (cadence <> 'WEEKLY' OR day_of_week IS NOT NULL)
);

CREATE TABLE forecast_job (
    job_id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenant(tenant_id),
    schedule_id UUID REFERENCES forecast_schedule(schedule_id),
    parent_job_id UUID REFERENCES forecast_job(job_id),
    forecast_run_id UUID REFERENCES forecast_run(forecast_run_id),
    status VARCHAR(30) NOT NULL CHECK (status IN ('QUEUED','RUNNING','COMPLETED','COMPLETED_WITH_ERRORS','FAILED','CANCELLED')),
    as_of_date DATE,
    horizon_days INTEGER NOT NULL CHECK (horizon_days IN (7,30,90)),
    history_days INTEGER CHECK (history_days BETWEEN 28 AND 365),
    warehouse_id VARCHAR(64) REFERENCES warehouse(warehouse_id),
    sku_id VARCHAR(80) REFERENCES sku(sku_id),
    attempt_number INTEGER NOT NULL DEFAULT 1 CHECK (attempt_number BETWEEN 1 AND 10),
    scheduled_for TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_by VARCHAR(128) NOT NULL REFERENCES app_user(user_id),
    error_message VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_forecast_schedule_occurrence UNIQUE (schedule_id,scheduled_for)
);

CREATE TABLE forecast_governance_alert (
    alert_id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenant(tenant_id),
    forecast_job_id UUID NOT NULL REFERENCES forecast_job(job_id) ON DELETE CASCADE,
    forecast_run_id UUID REFERENCES forecast_run(forecast_run_id) ON DELETE CASCADE,
    alert_type VARCHAR(40) NOT NULL CHECK (alert_type IN ('STALE_DATA','LOW_CONFIDENCE','POSITION_FAILURES','RUN_FAILURE')),
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('INFO','WARNING','CRITICAL')),
    message VARCHAR(1000) NOT NULL,
    acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    acknowledged_by VARCHAR(128) REFERENCES app_user(user_id),
    acknowledged_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_forecast_schedule_due ON forecast_schedule(active,next_run_at);
CREATE INDEX idx_forecast_job_queue ON forecast_job(status,scheduled_for,created_at);
CREATE INDEX idx_forecast_job_tenant_time ON forecast_job(tenant_id,created_at DESC);
CREATE INDEX idx_forecast_alert_tenant_open ON forecast_governance_alert(tenant_id,acknowledged,severity,created_at DESC);
