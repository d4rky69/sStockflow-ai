CREATE TABLE forecast_configuration (
    forecast_configuration_id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    default_history_days INTEGER NOT NULL CHECK (default_history_days BETWEEN 28 AND 365),
    backtest_days INTEGER NOT NULL CHECK (backtest_days BETWEEN 7 AND 90),
    moving_average_window INTEGER NOT NULL CHECK (moving_average_window BETWEEN 2 AND 30),
    seasonal_period_days INTEGER NOT NULL CHECK (seasonal_period_days BETWEEN 2 AND 30),
    minimum_history_days INTEGER NOT NULL CHECK (minimum_history_days BETWEEN 14 AND 365),
    enabled_models VARCHAR(300) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_forecast_configuration_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id),
    CONSTRAINT uq_forecast_configuration_tenant UNIQUE (tenant_id)
);

CREATE TABLE forecast_run (
    forecast_run_id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    as_of_date DATE NOT NULL,
    horizon_days INTEGER NOT NULL CHECK (horizon_days IN (7, 30, 90)),
    history_days INTEGER NOT NULL CHECK (history_days BETWEEN 28 AND 365),
    requested_warehouse_id VARCHAR(64),
    requested_sku_id VARCHAR(80),
    status VARCHAR(40) NOT NULL,
    positions_requested INTEGER NOT NULL DEFAULT 0,
    positions_processed INTEGER NOT NULL DEFAULT 0,
    positions_failed INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_forecast_run_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id),
    CONSTRAINT fk_forecast_run_warehouse FOREIGN KEY (requested_warehouse_id) REFERENCES warehouse(warehouse_id),
    CONSTRAINT fk_forecast_run_sku FOREIGN KEY (requested_sku_id) REFERENCES sku(sku_id)
);

CREATE INDEX idx_forecast_run_tenant_started ON forecast_run(tenant_id, started_at);
CREATE INDEX idx_forecast_run_tenant_status ON forecast_run(tenant_id, status);

CREATE TABLE forecast_model_performance (
    forecast_model_performance_id UUID PRIMARY KEY,
    forecast_run_id UUID NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    warehouse_id VARCHAR(64) NOT NULL,
    sku_id VARCHAR(80) NOT NULL,
    model_code VARCHAR(60) NOT NULL,
    training_sample_count INTEGER NOT NULL,
    backtest_points INTEGER NOT NULL,
    mae NUMERIC(19, 6) NOT NULL,
    rmse NUMERIC(19, 6) NOT NULL,
    mape NUMERIC(19, 6),
    bias NUMERIC(19, 6) NOT NULL,
    selected_model BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_forecast_performance_run FOREIGN KEY (forecast_run_id) REFERENCES forecast_run(forecast_run_id) ON DELETE CASCADE,
    CONSTRAINT fk_forecast_performance_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id),
    CONSTRAINT fk_forecast_performance_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse(warehouse_id),
    CONSTRAINT fk_forecast_performance_sku FOREIGN KEY (sku_id) REFERENCES sku(sku_id),
    CONSTRAINT uq_forecast_performance_model UNIQUE (forecast_run_id, warehouse_id, sku_id, model_code)
);

CREATE INDEX idx_forecast_performance_run ON forecast_model_performance(forecast_run_id);
CREATE INDEX idx_forecast_performance_position ON forecast_model_performance(tenant_id, warehouse_id, sku_id);

CREATE TABLE forecast_result (
    forecast_result_id UUID PRIMARY KEY,
    forecast_run_id UUID NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    warehouse_id VARCHAR(64) NOT NULL,
    sku_id VARCHAR(80) NOT NULL,
    forecast_date DATE NOT NULL,
    horizon_day INTEGER NOT NULL CHECK (horizon_day BETWEEN 1 AND 90),
    model_code VARCHAR(60) NOT NULL,
    forecast_quantity NUMERIC(19, 4) NOT NULL CHECK (forecast_quantity >= 0),
    lower_bound NUMERIC(19, 4) NOT NULL CHECK (lower_bound >= 0),
    upper_bound NUMERIC(19, 4) NOT NULL CHECK (upper_bound >= 0),
    confidence VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_forecast_result_run FOREIGN KEY (forecast_run_id) REFERENCES forecast_run(forecast_run_id) ON DELETE CASCADE,
    CONSTRAINT fk_forecast_result_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id),
    CONSTRAINT fk_forecast_result_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse(warehouse_id),
    CONSTRAINT fk_forecast_result_sku FOREIGN KEY (sku_id) REFERENCES sku(sku_id),
    CONSTRAINT uq_forecast_result_day UNIQUE (forecast_run_id, warehouse_id, sku_id, forecast_date)
);

CREATE INDEX idx_forecast_result_run ON forecast_result(forecast_run_id);
CREATE INDEX idx_forecast_result_position_date ON forecast_result(tenant_id, warehouse_id, sku_id, forecast_date);

CREATE TABLE forecast_exception (
    forecast_exception_id UUID PRIMARY KEY,
    forecast_run_id UUID NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    warehouse_id VARCHAR(64),
    sku_id VARCHAR(80),
    exception_code VARCHAR(60) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_forecast_exception_run FOREIGN KEY (forecast_run_id) REFERENCES forecast_run(forecast_run_id) ON DELETE CASCADE,
    CONSTRAINT fk_forecast_exception_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id)
);

CREATE INDEX idx_forecast_exception_run ON forecast_exception(forecast_run_id);
