# Phase 2 Increment 4 — Inventory Intelligence and Database-backed Dashboard

## Objective

Increment 4 converts StockFlow AI from a sales-reporting foundation into a deterministic inventory-intelligence service. All calculations are tenant-scoped and use PostgreSQL data imported in Increments 2 and 3.

## Delivered capabilities

### Demand analytics

- Demand summary for configurable windows from 1 to 365 days
- Warehouse/SKU-level demand ranking
- Weekly actual-demand trend with deterministic moving-average forecast
- Sales, returns, lost sales, stockout rows, value and fulfilment metrics

### Inventory intelligence

For the latest inventory snapshot per tenant, the service calculates:

- Available quantity
- Reserved quantity
- Blocked quantity
- Usable quantity
- Inventory value
- Reserved/blocked value
- Average daily demand
- Days of cover
- Safety-stock position
- Earliest batch expiry

### Deterministic risk engine

Default thresholds are configurable in `application.yml`:

| Rule | Default |
|---|---:|
| Stockout-risk cover | 14 days |
| Critical stockout cover | 7 days |
| Excess-inventory cover | 90 days |
| Near-expiry window | 60 days |
| Demand-surge threshold | 50% |

Risk types:

- `STOCKOUT_RISK`
- `SAFETY_STOCK_BREACH`
- `INVENTORY_DATA_GAP`
- `NEAR_EXPIRY`
- `EXPIRED_INVENTORY`
- `EXCESS_INVENTORY`
- `SLOW_MOVING`
- `DEMAND_SURGE`

`INVENTORY_DATA_GAP` is emitted when recent demand exists but the latest snapshot has no warehouse-SKU inventory record. It is a data-quality alert and is excluded from confirmed stockout counts and operational recommendations.

Each risk includes a reason and deterministic recommended action.

### Database-backed dashboard

`GET /api/v1/dashboard/overview` no longer returns the Sprint 1 fixture. It now returns live tenant-scoped values from PostgreSQL for:

- Inventory value
- Stockout/safety risks
- Near-expiry exposure
- Excess/slow-moving inventory
- Reserved/blocked value
- Risk breakdown
- Top risks
- Recommendations
- Demand trend
- Warehouse, retailer, SKU and batch counts

The Angular dashboard sends `X-Tenant-ID`. The local default is `TEN-ACME-PHARMA`; it can be changed through browser local storage key `stockflowTenantId`.

## APIs

```http
GET /api/v1/analytics/demand/summary?windowDays=30
GET /api/v1/analytics/demand/skus?windowDays=30&limit=25
GET /api/v1/analytics/demand/trend?weeks=16

GET /api/v1/risks/summary
GET /api/v1/risks/inventory?type=STOCKOUT_RISK&severity=CRITICAL&limit=100
GET /api/v1/risks/stockout?limit=100
GET /api/v1/risks/expiry?days=60&limit=100

GET /api/v1/dashboard/overview
```

Every endpoint requires:

```http
X-Tenant-ID: TEN-ACME-PHARMA
```

## Database migration

`V009__add_inventory_intelligence_indexes.sql` adds composite indexes for the latest-snapshot and demand-window queries. No imported data is deleted or rewritten.

## Expected synthetic-data risk profile

With the prepared foundation and sales packages fully imported:

| Tenant | Total alerts | Operational stock risk | Inventory data gaps | Demand surge | Near expiry | Excess |
|---|---:|---:|---:|---:|---:|---:|
| Acme Pharma | 147 | 16 | 117 | 13 | 1 | 0 |

The Acme profile was verified against the database: 148 warehouse-SKU demand positions exist, 31 have inventory snapshots and 117 are missing snapshots. Missing snapshots are surfaced separately and are not presented as confirmed stockouts.

## Test coverage

The backend contains 10 integration tests covering:

- Foundation queries
- Foundation import validation and upsert
- Sales import and analytics
- Demand analytics
- Stockout and expiry risk detection
- Database-backed dashboard response
