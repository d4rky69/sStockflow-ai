ALTER TABLE forecast_configuration
    ADD COLUMN minimum_non_zero_observations INTEGER NOT NULL DEFAULT 8;

ALTER TABLE forecast_configuration
    ADD COLUMN weekly_aggregation_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE forecast_configuration
    ADD COLUMN high_confidence_mase NUMERIC(7, 3) NOT NULL DEFAULT 0.800;

ALTER TABLE forecast_configuration
    ADD COLUMN medium_confidence_mase NUMERIC(7, 3) NOT NULL DEFAULT 1.500;

ALTER TABLE forecast_configuration
    ADD COLUMN maximum_forecastable_cv_squared NUMERIC(9, 4) NOT NULL DEFAULT 10.0000;

ALTER TABLE forecast_configuration
    ADD CONSTRAINT ck_forecast_configuration_non_zero CHECK (minimum_non_zero_observations >= 1);

ALTER TABLE forecast_configuration
    ADD CONSTRAINT ck_forecast_configuration_mase CHECK (
        high_confidence_mase >= 0
        AND medium_confidence_mase >= high_confidence_mase
    );

ALTER TABLE forecast_configuration
    ADD CONSTRAINT ck_forecast_configuration_cv2 CHECK (maximum_forecastable_cv_squared > 0);

UPDATE forecast_configuration
SET enabled_models =
    'NAIVE,MOVING_AVERAGE,WEIGHTED_MOVING_AVERAGE,SEASONAL_NAIVE,' ||
    'SIMPLE_EXPONENTIAL_SMOOTHING,HOLT_LINEAR_TREND,HOLT_WINTERS_ADDITIVE,' ||
    'CROSTON_CLASSIC,CROSTON_SBA,TSB',
    updated_at = CURRENT_TIMESTAMP;

ALTER TABLE forecast_model_performance
    DROP CONSTRAINT uq_forecast_performance_model;

ALTER TABLE forecast_model_performance
    ADD COLUMN aggregation_level VARCHAR(20) NOT NULL DEFAULT 'DAILY';

ALTER TABLE forecast_model_performance
    ADD COLUMN eligibility_status VARCHAR(50) NOT NULL DEFAULT 'ELIGIBLE';

ALTER TABLE forecast_model_performance
    ADD COLUMN non_zero_observations INTEGER NOT NULL DEFAULT 0;

ALTER TABLE forecast_model_performance
    ADD COLUMN average_demand_interval NUMERIC(19, 6) NOT NULL DEFAULT 0;

ALTER TABLE forecast_model_performance
    ADD COLUMN coefficient_variation_squared NUMERIC(19, 6) NOT NULL DEFAULT 0;

ALTER TABLE forecast_model_performance
    ADD COLUMN mase NUMERIC(19, 6);

ALTER TABLE forecast_model_performance
    ADD COLUMN rmsse NUMERIC(19, 6);

ALTER TABLE forecast_model_performance
    ADD CONSTRAINT ck_forecast_performance_non_zero CHECK (non_zero_observations >= 0);

ALTER TABLE forecast_model_performance
    ADD CONSTRAINT ck_forecast_performance_adi CHECK (average_demand_interval >= 0);

ALTER TABLE forecast_model_performance
    ADD CONSTRAINT ck_forecast_performance_cv2 CHECK (coefficient_variation_squared >= 0);

ALTER TABLE forecast_model_performance
    ADD CONSTRAINT ck_forecast_performance_mase CHECK (mase IS NULL OR mase >= 0);

ALTER TABLE forecast_model_performance
    ADD CONSTRAINT ck_forecast_performance_rmsse CHECK (rmsse IS NULL OR rmsse >= 0);

ALTER TABLE forecast_model_performance
    ADD CONSTRAINT uq_forecast_performance_model_aggregation UNIQUE (
        forecast_run_id,
        warehouse_id,
        sku_id,
        model_code,
        aggregation_level
    );

CREATE INDEX idx_forecast_performance_aggregation
    ON forecast_model_performance(forecast_run_id, aggregation_level, selected_model);

CREATE TABLE forecast_position_diagnostic (
    forecast_position_diagnostic_id UUID PRIMARY KEY,
    forecast_run_id UUID NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    warehouse_id VARCHAR(64) NOT NULL,
    sku_id VARCHAR(80) NOT NULL,
    eligibility_status VARCHAR(50) NOT NULL,
    demand_pattern VARCHAR(30) NOT NULL,
    selected_aggregation VARCHAR(20) NOT NULL,
    history_observations INTEGER NOT NULL,
    non_zero_observations INTEGER NOT NULL,
    zero_demand_ratio NUMERIC(9, 6) NOT NULL,
    average_demand_interval NUMERIC(19, 6) NOT NULL,
    coefficient_variation_squared NUMERIC(19, 6) NOT NULL,
    outliers_adjusted INTEGER NOT NULL,
    selected_model VARCHAR(60),
    selected_wape NUMERIC(19, 6),
    selected_mase NUMERIC(19, 6),
    selected_rmsse NUMERIC(19, 6),
    best_daily_wape NUMERIC(19, 6),
    reason_codes VARCHAR(1200) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_forecast_diagnostic_run FOREIGN KEY (forecast_run_id)
        REFERENCES forecast_run(forecast_run_id) ON DELETE CASCADE,
    CONSTRAINT fk_forecast_diagnostic_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenant(tenant_id),
    CONSTRAINT fk_forecast_diagnostic_warehouse FOREIGN KEY (warehouse_id)
        REFERENCES warehouse(warehouse_id),
    CONSTRAINT fk_forecast_diagnostic_sku FOREIGN KEY (sku_id)
        REFERENCES sku(sku_id),
    CONSTRAINT uq_forecast_diagnostic_position UNIQUE (forecast_run_id, warehouse_id, sku_id),
    CONSTRAINT ck_forecast_diagnostic_history CHECK (history_observations >= 0),
    CONSTRAINT ck_forecast_diagnostic_non_zero CHECK (non_zero_observations >= 0),
    CONSTRAINT ck_forecast_diagnostic_zero_ratio CHECK (zero_demand_ratio BETWEEN 0 AND 1),
    CONSTRAINT ck_forecast_diagnostic_adi CHECK (average_demand_interval >= 0),
    CONSTRAINT ck_forecast_diagnostic_cv2 CHECK (coefficient_variation_squared >= 0),
    CONSTRAINT ck_forecast_diagnostic_outliers CHECK (outliers_adjusted >= 0)
);

CREATE INDEX idx_forecast_diagnostic_run
    ON forecast_position_diagnostic(forecast_run_id, eligibility_status, selected_aggregation);

CREATE INDEX idx_forecast_diagnostic_position
    ON forecast_position_diagnostic(tenant_id, warehouse_id, sku_id, created_at);
