# StockFlow AI — Phase 3 Increment 5B.1

## Forecast Calibration and Diagnostics

This overlay must be applied on top of Phase 3 Increment 5B.

## What it adds

- ADI and CV² demand classification
- Daily-versus-weekly model calibration
- Croston Classic
- TSB
- MASE and RMSSE
- Forecast eligibility controls
- Position-level diagnostic reason codes
- Forecast diagnostic APIs
- Calibration summary API
- Flyway migration V012
- Kotlin unit and integration test updates
- Windows run and verification scripts

## Apply the package

Stop the running backend using `Ctrl+C`.

Extract this ZIP. Copy everything inside:

```text
StockFlow_AI_Phase3_Increment5B1_Forecast_Calibration_Diagnostics
```

into:

```text
C:\Users\oveyj\Downloads\StockFlow_AI_Phase2_Increment4
```

Choose **Replace the files in the destination**.

## Confirm the package was merged

```cmd
cd /d C:\Users\oveyj\Downloads\StockFlow_AI_Phase2_Increment4

dir run-core-api-phase3-forecast-calibration-windows.cmd

dir services\stockflow-core-api\src\main\resources\db\migration\V012__add_forecast_calibration_and_diagnostics.sql
```

## Run tests and start the backend

```cmd
run-core-api-phase3-forecast-calibration-windows.cmd
```

The launcher runs the complete Maven test suite before starting Spring Boot.

Expected:

```text
BUILD SUCCESS
Tomcat started on port 8080
Started StockFlowApplicationKt
```

Keep that terminal open.

## Verify Increment 5B.1

Open a second CMD window:

```cmd
cd /d C:\Users\oveyj\Downloads\StockFlow_AI_Phase2_Increment4

verify-forecast-calibration-api-windows.cmd
```

Expected HTTP results:

```text
Health:               UP
Configuration:        HTTP 200
Forecast creation:    HTTP 201
Latest forecast:      HTTP 200
Position diagnostic:  HTTP 200
Calibration summary:  HTTP 200
Model performance:    HTTP 200
Accuracy summary:     HTTP 200
```

## Tenant-wide calibration run

After focused verification succeeds:

```cmd
curl -i -X POST -H "Content-Type: application/json" -H "X-Tenant-ID: TEN-ACME-PHARMA" -d "{\"horizonDays\":7,\"historyDays\":180}" "http://localhost:8080/api/v1/forecasts/runs"
```

Then inspect calibration:

```cmd
curl -H "X-Tenant-ID: TEN-ACME-PHARMA" "http://localhost:8080/api/v1/forecasts/calibration-summary"
```

```cmd
curl -H "X-Tenant-ID: TEN-ACME-PHARMA" "http://localhost:8080/api/v1/forecasts/diagnostics?limit=250"
```

## Important

The frontend does not need to be replaced for this backend increment. Existing forecast JSON remains backward-compatible, with additional diagnostic fields.

The latest Phase 3 backend should be deployed to Cloud Run only after local Maven tests and tenant-wide calibration verification pass.
