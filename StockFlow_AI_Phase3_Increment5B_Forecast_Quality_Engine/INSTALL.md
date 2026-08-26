# StockFlow AI — Phase 3 Increment 5B Installation

## Purpose

Increment 5B upgrades the forecasting engine with advanced statistical models, intermittent-demand handling, outlier treatment, WAPE/sMAPE scoring, tenant configuration, and aggregate accuracy reporting.

## Prerequisite

Phase 3 Increment 5A must already be installed and working.

Your project root should be:

```text
C:\Users\oveyj\Downloads\StockFlow_AI_Phase2_Increment4
```

## Apply

1. Stop the running backend with `Ctrl+C`.
2. Extract the Increment 5B ZIP.
3. Open the extracted folder:

```text
StockFlow_AI_Phase3_Increment5B_Forecast_Quality_Engine
```

4. Copy everything inside that folder into:

```text
C:\Users\oveyj\Downloads\StockFlow_AI_Phase2_Increment4
```

5. Choose **Replace the files in the destination**.

The overlay does not replace the Angular frontend.

## Run tests and start the backend

```cmd
cd /d C:\Users\oveyj\Downloads\StockFlow_AI_Phase2_Increment4
run-core-api-phase3-forecast-quality-windows.cmd
```

The launcher runs Maven tests first. The backend starts only after the tests pass.

Flyway should apply:

```text
V011__enhance_forecast_quality_engine.sql
```

## Verify

Keep the backend terminal open. In a second CMD window:

```cmd
cd /d C:\Users\oveyj\Downloads\StockFlow_AI_Phase2_Increment4
verify-forecast-quality-api-windows.cmd
```

Expected results:

- Health endpoint: `UP`
- Forecast configuration: HTTP `200`
- Forecast run creation: HTTP `201`
- Latest forecast: HTTP `200`
- Model performance: HTTP `200`
- Accuracy summary: HTTP `200`
- Candidate-model rows for a focused run: `8`

## New models

```text
SIMPLE_EXPONENTIAL_SMOOTHING
HOLT_LINEAR_TREND
HOLT_WINTERS_ADDITIVE
CROSTON_SBA
```

These are evaluated together with the four Increment 5A models.

## New endpoints

```http
GET /api/v1/forecasts/configuration
PUT /api/v1/forecasts/configuration
GET /api/v1/forecasts/accuracy-summary
```

## Important

Do not deploy Increment 5B to Cloud Run until the local Maven tests and focused verification script both pass.
