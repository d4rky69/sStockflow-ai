SELECT version, description, success
FROM flyway_schema_history
WHERE version IN ('10', '11', '12')
ORDER BY installed_rank;

SELECT
    weekly_aggregation_enabled,
    minimum_non_zero_observations,
    high_confidence_mase,
    medium_confidence_mase,
    maximum_forecastable_cv_squared,
    enabled_models
FROM forecast_configuration
WHERE tenant_id = 'TEN-ACME-PHARMA';

SELECT
    eligibility_status,
    selected_aggregation,
    demand_pattern,
    COUNT(*) AS positions
FROM forecast_position_diagnostic
WHERE tenant_id = 'TEN-ACME-PHARMA'
GROUP BY eligibility_status, selected_aggregation, demand_pattern
ORDER BY eligibility_status, selected_aggregation, demand_pattern;

SELECT
    model_code,
    aggregation_level,
    COUNT(*) FILTER (WHERE selected_model) AS selected_count,
    ROUND(AVG(wape), 4) AS average_wape,
    ROUND(AVG(mase), 4) AS average_mase,
    ROUND(AVG(rmsse), 4) AS average_rmsse
FROM forecast_model_performance
WHERE tenant_id = 'TEN-ACME-PHARMA'
GROUP BY model_code, aggregation_level
ORDER BY selected_count DESC, model_code, aggregation_level;
