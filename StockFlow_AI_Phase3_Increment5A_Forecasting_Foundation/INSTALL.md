# StockFlow AI — Phase 3 Increment 5A Installation

This is an **overlay package** for the current repository. It contains only the new and modified backend files. It does not replace the frontend or the current README.

## Current repository

```text
C:\Users\oveyj\Downloads\StockFlow_AI_Phase2_Increment4
```

## Apply the overlay

1. Stop the backend using `Ctrl+C`.
2. Back up or commit the current backend changes.
3. Extract this ZIP.
4. Copy everything inside the extracted folder into:

```text
C:\Users\oveyj\Downloads\StockFlow_AI_Phase2_Increment4
```

5. Choose **Replace the files in the destination** and allow Windows to merge folders.

The overlay modifies only:

```text
services\stockflow-core-api
contracts
backend documentation
backend run and verification scripts
```

## Run tests

```cmd
cd /d C:\Users\oveyj\Downloads\StockFlow_AI_Phase2_Increment4\services\stockflow-core-api

"C:\Users\oveyj\Tools\apache-maven-3.9.16\bin\mvn.cmd" ^
  -Dkotlin.compiler.daemon=false clean test
```

Expected after Increment 5A:

```text
Tests run: 12
Failures: 0
Errors: 0
BUILD SUCCESS
```

The exact test count can be higher when newer tests already exist in the repository.

## Start the backend

```cmd
cd /d C:\Users\oveyj\Downloads\StockFlow_AI_Phase2_Increment4
run-core-api-phase3-forecasting-windows.cmd
```

Flyway applies:

```text
V010__create_forecasting_foundation.sql
```

## Verify the API

In another CMD window:

```cmd
cd /d C:\Users\oveyj\Downloads\StockFlow_AI_Phase2_Increment4
verify-forecasting-api-windows.cmd
```

## First endpoint

```text
POST http://localhost:8080/api/v1/forecasts/runs
```

Required header:

```text
X-Tenant-ID: TEN-ACME-PHARMA
```

## Important

Start with one warehouse and SKU. After that succeeds, run a tenant-wide 7-day forecast. A tenant-wide 30-day or 90-day run creates significantly more persisted forecast rows.
