# StockFlow Route and Carbon Service

This FastAPI service contains the route and carbon backend integrated from Pari's prototype into the canonical StockFlow architecture.

## Endpoints

- `GET /health`
- `GET /api/v1/carbon/emission-factors`
- `POST /api/v1/carbon/calculate`
- `POST /api/v1/routes/distance-estimate`
- `POST /api/v1/routes/optimise`
- `POST /api/v1/transfers/recommend`

Business endpoints require the `X-Tenant-ID` header. The service returns its emission factors, calculation method and limitations so prototype results are explainable.

## Run locally

From the repository root:

```cmd
run-carbon-windows.cmd
```

The service starts on `http://127.0.0.1:8400`.

## Test

```cmd
cd services\carbon-service
.venv\Scripts\python.exe -m unittest discover -s tests -v
```

## Current scope

The current optimisation is a deterministic, explainable prototype. It accounts for baseline distance, objective, vehicle family, capacity utilisation, cost and estimated CO2e. It does not yet use live traffic, road-network time windows or the planned OR-Tools vehicle-routing solver.
