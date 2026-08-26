# StockFlow AI — Phase 2 Increment 4 Apply and Run

## What this increment adds

- Demand summary, SKU demand and demand-trend APIs
- Days-of-cover and safety-stock calculations
- Stockout, expiry, excess, slow-moving and demand-surge risk rules
- Database-backed dashboard API
- Angular tenant header
- Flyway V009 query indexes
- Two new integration tests, bringing the suite to 10 tests

## Option A — Use the complete Increment 4 project

1. Stop the currently running Increment 3 API with `Ctrl+C`.
2. Extract `StockFlow_AI_Phase2_Increment4_Inventory_Intelligence.zip` into `C:\Users\oveyj\Downloads`.
3. Open CMD:

```cmd
cd /d C:\Users\oveyj\Downloads\StockFlow_AI_Phase2_Increment4
```

4. Run tests:

```cmd
cd services\stockflow-core-api
"C:\Users\oveyj\Tools\apache-maven-3.9.16\bin\mvn.cmd" -Dkotlin.compiler.daemon=false clean test
```

Expected:

```text
Tests run: 10
Failures: 0
Errors: 0
BUILD SUCCESS
```

5. Return to the project root and start against the existing database:

```cmd
cd /d C:\Users\oveyj\Downloads\StockFlow_AI_Phase2_Increment4
set "STOCKFLOW_DB_URL=jdbc:postgresql://localhost:5433/stockflow_phase2"
set "STOCKFLOW_DB_USERNAME=stockflow_app"
set "STOCKFLOW_DB_PASSWORD=stockflow_dev"
call run-core-api-phase2-import-windows.cmd
```

Flyway applies `V009__add_inventory_intelligence_indexes.sql` and preserves all existing imported rows.

## Option B — Apply the patch to your corrected Increment 3 folder

From:

```cmd
cd /d C:\Users\oveyj\Downloads\StockFlow_AI_Phase2_Increment3
```

Check:

```cmd
git apply --check "C:\Users\oveyj\Downloads\StockFlow_AI_Phase2_Increment4_Core.patch"
```

Apply:

```cmd
git apply "C:\Users\oveyj\Downloads\StockFlow_AI_Phase2_Increment4_Core.patch"
```

Then run the same Maven test and startup commands.

## Verification commands

Health:

```cmd
curl http://localhost:8080/actuator/health
```

Demand summary:

```cmd
curl -H "X-Tenant-ID: TEN-ACME-PHARMA" "http://localhost:8080/api/v1/analytics/demand/summary?windowDays=30"
```

Risk summary:

```cmd
curl -H "X-Tenant-ID: TEN-ACME-PHARMA" http://localhost:8080/api/v1/risks/summary
```

Top stockout risks:

```cmd
curl -H "X-Tenant-ID: TEN-ACME-PHARMA" "http://localhost:8080/api/v1/risks/stockout?limit=10"
```

Near-expiry risks:

```cmd
curl -H "X-Tenant-ID: TEN-ACME-PHARMA" "http://localhost:8080/api/v1/risks/expiry?days=60&limit=10"
```

Database-backed dashboard:

```cmd
curl -H "X-Tenant-ID: TEN-ACME-PHARMA" http://localhost:8080/api/v1/dashboard/overview
```

## Expected Acme risk summary

With the prepared dataset already imported, the default rules should produce approximately:

```text
asOfDate: 2026-07-01
totalRisks: 147
stockoutRiskCount: 133
nearExpiryCount: 1
demandSurgeCount: 13
```

The exact response also includes severity counts and monetary exposure.
