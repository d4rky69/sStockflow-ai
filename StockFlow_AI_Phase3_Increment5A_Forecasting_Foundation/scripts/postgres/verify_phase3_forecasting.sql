SELECT table_name
FROM information_schema.tables
WHERE table_name IN (
    'forecast_configuration',
    'forecast_run',
    'forecast_model_performance',
    'forecast_result',
    'forecast_exception'
)
ORDER BY table_name;

SELECT tenant_id, status, as_of_date, horizon_days,
       positions_requested, positions_processed, positions_failed,
       started_at, completed_at
FROM forecast_run
ORDER BY started_at DESC
LIMIT 10;

SELECT model_code,
       COUNT(*) AS evaluated_positions,
       SUM(CASE WHEN selected_model THEN 1 ELSE 0 END) AS selected_positions,
       ROUND(AVG(mae), 4) AS average_mae,
       ROUND(AVG(rmse), 4) AS average_rmse,
       ROUND(AVG(mape), 4) AS average_mape
FROM forecast_model_performance
GROUP BY model_code
ORDER BY model_code;

SELECT tenant_id, warehouse_id, sku_id,
       MIN(forecast_date) AS first_forecast_date,
       MAX(forecast_date) AS last_forecast_date,
       SUM(forecast_quantity) AS forecast_quantity
FROM forecast_result
GROUP BY tenant_id, warehouse_id, sku_id
ORDER BY tenant_id, warehouse_id, sku_id
LIMIT 50;
