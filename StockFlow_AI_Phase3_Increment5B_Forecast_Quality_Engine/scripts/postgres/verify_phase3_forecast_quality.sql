SELECT column_name, data_type
FROM information_schema.columns
WHERE table_name = 'forecast_model_performance'
  AND column_name IN ('demand_pattern', 'zero_demand_ratio', 'outliers_adjusted', 'wape', 'smape', 'selection_score')
ORDER BY column_name;

SELECT model_code, COUNT(*) AS evaluated_positions,
       ROUND(AVG(wape), 2) AS average_wape,
       ROUND(AVG(smape), 2) AS average_smape,
       SUM(CASE WHEN selected_model THEN 1 ELSE 0 END) AS selected_count
FROM forecast_model_performance
GROUP BY model_code
ORDER BY average_wape;

SELECT demand_pattern, COUNT(DISTINCT warehouse_id || ':' || sku_id) AS positions,
       ROUND(AVG(zero_demand_ratio), 4) AS average_zero_ratio,
       SUM(outliers_adjusted) AS outliers_adjusted
FROM forecast_model_performance
WHERE selected_model = TRUE
GROUP BY demand_pattern
ORDER BY demand_pattern;
