# Phase 3 Increment 5B.1 — Forecast Calibration and Diagnostics

## Purpose

Increment 5B proved that the forecasting engine could process the complete tenant scope, but all 148 Acme Pharma positions were classified as low confidence. Increment 5B.1 improves the forecasting decision process before operational scheduling or replenishment automation is introduced.

## Delivered capabilities

### Demand classification

Each warehouse-SKU position now records:

- Average Demand Interval (ADI)
- Squared coefficient of variation (CV²)
- Non-zero observation count
- Zero-demand ratio
- Outlier-adjustment count
- Demand pattern: `SMOOTH`, `ERRATIC`, `INTERMITTENT`, or `LUMPY`

The standard ADI/CV² boundaries are used:

| Pattern | ADI | CV² |
|---|---:|---:|
| Smooth | `< 1.32` | `< 0.49` |
| Erratic | `< 1.32` | `>= 0.49` |
| Intermittent | `>= 1.32` | `< 0.49` |
| Lumpy | `>= 1.32` | `>= 0.49` |

### Daily versus weekly calibration

For eligible positions, the engine evaluates:

- Daily demand history
- Weekly aggregated demand history

The winning model-frequency combination is selected using scaled error and WAPE. Weekly forecasts are disaggregated into daily values so the existing forecast-result contract remains compatible with stockout projection and frontend charts.

### Additional intermittent-demand models

The candidate model set now contains ten models:

1. Naive
2. Moving Average
3. Weighted Moving Average
4. Seasonal Naive
5. Simple Exponential Smoothing
6. Holt Linear Trend
7. Holt-Winters Additive
8. Croston Classic
9. Croston SBA
10. TSB

Croston-family and TSB models are reserved for intermittent and lumpy demand patterns.

### Additional accuracy metrics

The engine now persists:

- MAE
- RMSE
- MAPE
- WAPE
- sMAPE
- MASE
- RMSSE
- Bias
- Composite selection score

MASE and RMSSE compare model error against a one-step naive baseline and are more useful than MAPE when the history contains many zero-demand observations.

### Forecast eligibility

Each position is classified as:

- `ELIGIBLE`
- `LIMITED_HISTORY`
- `INSUFFICIENT_NON_ZERO_DEMAND`
- `DATA_GAP`
- `TOO_VOLATILE`
- `NOT_FORECASTABLE`

Ineligible positions are not forced through a model. They are recorded in the diagnostic and exception tables with explicit reason codes.

### Diagnostic reason codes

Supported reason codes include:

- `INTERMITTENT_DEMAND`
- `LUMPY_DEMAND`
- `HIGH_ZERO_DEMAND_RATIO`
- `INSUFFICIENT_NON_ZERO_HISTORY`
- `HIGH_DEMAND_VARIABILITY`
- `OUTLIER_HEAVY_HISTORY`
- `NO_MEANINGFUL_SEASONALITY`
- `LOW_BACKTEST_ACCURACY`
- `WEEKLY_AGGREGATION_SELECTED`
- `DAILY_AGGREGATION_SELECTED`
- `LIMITED_HISTORY`
- `NO_POSITIVE_DEMAND`

## Database migration

Flyway migration:

```text
V012__add_forecast_calibration_and_diagnostics.sql
```

It adds calibration configuration, aggregation and scaled-error fields, new model codes, and the `forecast_position_diagnostic` table.

## New APIs

```http
GET /api/v1/forecasts/diagnostics
GET /api/v1/forecasts/diagnostics/{warehouseId}/{skuId}
GET /api/v1/forecasts/calibration-summary
```

Existing forecast APIs now include aggregation, eligibility, ADI, CV², MASE, RMSSE, non-zero observation count, and diagnostic reasons.

## Configuration additions

```text
minimumNonZeroObservations
weeklyAggregationEnabled
highConfidenceMase
mediumConfidenceMase
maximumForecastableCvSquared
```

## Selection and confidence controls

Model selection prioritizes MASE when it is available and uses WAPE as a secondary measure. Pattern-aware penalties discourage inappropriate model families.

High and medium confidence now require both:

- MASE within the configured threshold
- WAPE within the configured threshold

Low-confidence forecasts remain advisory only.

## Next increment

After tenant-wide calibration is verified, proceed to Increment 5C:

- Scheduled forecast execution
- Retry and exception processing
- Forecast-run monitoring
- Performance history
- Accuracy-degradation alerts
- Approval and minimum-confidence controls
