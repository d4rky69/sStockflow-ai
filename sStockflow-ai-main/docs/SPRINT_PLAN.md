# StockFlow AI Sprint Plan

## Delivery model

StockFlow AI is built as one integrated product. The sprints are incremental vertical slices, not separate applications.

## Sprint 1 — UI and contract foundation

**Implemented in this package**

- Angular application shell and responsive dashboard
- Mock data and backend fallback
- Dashboard REST contract
- Core API, intelligence and MCP scaffolding
- Synthetic data generator and sample files

**Exit criteria**

- Dashboard runs independently with mock data
- Kotlin API exposes the same dashboard contract
- Repository structure supports later sprints without restructuring

## Sprint 2 — Core backend and data

- PostgreSQL schema and Flyway migrations
- Tenant, warehouse, product, SKU, batch inventory and sales modules
- CSV import pipeline and validation report
- Dashboard aggregation from persisted data
- API tests and tenant-isolation tests

## Sprint 3 — Forecasting and optimisation

- Feature preparation
- Baseline, seasonal and gradient-boosting forecasts
- Backtesting and forecast metrics
- Stockout, excess and near-expiry risks
- FEFO stock rebalancing
- Purchase-versus-transfer comparison
- Financial impact calculation

## Sprint 4 — MCP and copilot

- Data MCP backed by the core API
- Intelligence MCP backed by forecast/optimisation services
- Copilot host and chat API
- Tool-call trace and evidence display
- Prompt-injection and output-filter controls

## Sprint 5 — Controlled proposals and approval

- Action MCP
- Transfer, purchase and markdown proposals
- Human confirmation and approval workflow
- Audit events and outcome measurement
- No autonomous inventory or financial execution

## Phase 2 implementation status

- **Increment 1 complete:** PostgreSQL/Flyway persistence foundation for tenant, warehouse, product, SKU and batch inventory.
- **Increment 2 implemented:** controlled tenant-scoped ZIP/CSV validation and import with job/error evidence and a Phase 2-ready synthetic foundation package.
- **Next — Increment 3:** sales-history persistence, scalable batch import, demand aggregation and database-backed dashboard KPIs.
