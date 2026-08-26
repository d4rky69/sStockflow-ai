# Phase 3 Increment 5B — Forecast Quality Engine

## Objective

Improve forecast accuracy, zero-demand handling and explainability before replenishment optimization is introduced.

## New candidate models

- Simple exponential smoothing
- Holt linear trend
- Holt-Winters additive seasonality
- Croston SBA for intermittent demand

These models are evaluated alongside the Increment 5A models:

- Naive
- Moving average
- Weighted moving average
- Seasonal naive

## Demand preparation

Every warehouse/SKU history is classified as one of:

- `SMOOTH`
- `ERRATIC`
- `INTERMITTENT`
- `LUMPY`

Zero-demand days remain in the time series. Optional Tukey-fence winsorization limits extreme positive-demand outliers without deleting observations.

## Quality metrics

Every candidate model is rolling-backtested using:

- MAE
- RMSE
- MAPE
- WAPE
- sMAPE
- Forecast bias

WAPE is the primary model-selection score because it remains useful when individual dates contain zero demand. MAPE is retained for comparison but is not used as the principal selector.

## Confidence

Confidence thresholds are tenant configurable:

- High confidence: WAPE at or below `highConfidenceWape` and at least 60 history days
- Medium confidence: WAPE at or below `mediumConfidenceWape` and at least 28 history days
- Low confidence: all other cases

Defaults:

```text
High confidence WAPE:   20%
Medium confidence WAPE: 40%
```

## Prediction intervals

Prediction intervals are based on selected-model RMSE and widen by the square root of the forecast horizon day.

## API additions

```http
GET /api/v1/forecasts/configuration
PUT /api/v1/forecasts/configuration
GET /api/v1/forecasts/accuracy-summary
```

Existing model-performance and latest-forecast responses now also include:

- demand pattern
- zero-demand ratio
- outliers adjusted
- WAPE
- sMAPE
- selection score

## Database migration

Flyway migration:

```text
V011__enhance_forecast_quality_engine.sql
```

The migration extends forecast configuration and model-performance tables. Existing forecast rows remain valid.

## Validation sequence

1. Run Maven tests.
2. Start the backend with the phase2 profile.
3. Run a focused forecast.
4. Confirm eight candidate-model rows.
5. Confirm the accuracy summary.
6. Run a tenant-wide forecast only after the focused test succeeds.
