ALTER TABLE forecast_configuration
    ADD COLUMN smoothing_alpha NUMERIC(5, 4) NOT NULL DEFAULT 0.3000;

ALTER TABLE forecast_configuration
    ADD COLUMN trend_beta NUMERIC(5, 4) NOT NULL DEFAULT 0.2000;

ALTER TABLE forecast_configuration
    ADD COLUMN seasonal_gamma NUMERIC(5, 4) NOT NULL DEFAULT 0.2000;

ALTER TABLE forecast_configuration
    ADD COLUMN high_confidence_wape NUMERIC(7, 2) NOT NULL DEFAULT 20.00;

ALTER TABLE forecast_configuration
    ADD COLUMN medium_confidence_wape NUMERIC(7, 2) NOT NULL DEFAULT 40.00;

ALTER TABLE forecast_configuration
    ADD COLUMN outlier_treatment_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE forecast_configuration
    ADD CONSTRAINT ck_forecast_configuration_alpha CHECK (smoothing_alpha BETWEEN 0.01 AND 0.99);

ALTER TABLE forecast_configuration
    ADD CONSTRAINT ck_forecast_configuration_beta CHECK (trend_beta BETWEEN 0.01 AND 0.99);

ALTER TABLE forecast_configuration
    ADD CONSTRAINT ck_forecast_configuration_gamma CHECK (seasonal_gamma BETWEEN 0.01 AND 0.99);

ALTER TABLE forecast_configuration
    ADD CONSTRAINT ck_forecast_configuration_confidence CHECK (
        high_confidence_wape >= 0
        AND medium_confidence_wape >= high_confidence_wape
    );

UPDATE forecast_configuration
SET enabled_models =
    'NAIVE,MOVING_AVERAGE,WEIGHTED_MOVING_AVERAGE,SEASONAL_NAIVE,' ||
    'SIMPLE_EXPONENTIAL_SMOOTHING,HOLT_LINEAR_TREND,HOLT_WINTERS_ADDITIVE,CROSTON_SBA',
    updated_at = CURRENT_TIMESTAMP;

ALTER TABLE forecast_model_performance
    ADD COLUMN demand_pattern VARCHAR(30) NOT NULL DEFAULT 'SMOOTH';

ALTER TABLE forecast_model_performance
    ADD COLUMN zero_demand_ratio NUMERIC(9, 6) NOT NULL DEFAULT 0;

ALTER TABLE forecast_model_performance
    ADD COLUMN outliers_adjusted INTEGER NOT NULL DEFAULT 0;

ALTER TABLE forecast_model_performance
    ADD COLUMN wape NUMERIC(19, 6) NOT NULL DEFAULT 0;

ALTER TABLE forecast_model_performance
    ADD COLUMN smape NUMERIC(19, 6) NOT NULL DEFAULT 0;

ALTER TABLE forecast_model_performance
    ADD COLUMN selection_score NUMERIC(19, 6) NOT NULL DEFAULT 0;

ALTER TABLE forecast_model_performance
    ADD CONSTRAINT ck_forecast_performance_zero_ratio CHECK (zero_demand_ratio BETWEEN 0 AND 1);

ALTER TABLE forecast_model_performance
    ADD CONSTRAINT ck_forecast_performance_outliers CHECK (outliers_adjusted >= 0);

ALTER TABLE forecast_model_performance
    ADD CONSTRAINT ck_forecast_performance_wape CHECK (wape >= 0);

ALTER TABLE forecast_model_performance
    ADD CONSTRAINT ck_forecast_performance_smape CHECK (smape >= 0);

CREATE INDEX idx_forecast_performance_quality
    ON forecast_model_performance(forecast_run_id, selected_model, demand_pattern);
