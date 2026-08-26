# StockFlow AI — Phase 2 Increment 1

## Persistent Database Foundation

This increment introduces the first real Phase 2 backend slice:

- PostgreSQL configuration
- Flyway-controlled schema
- Tenant, warehouse, product, SKU and batch-inventory tables
- Kotlin/JPA persistence entities and repositories
- Tenant-scoped read APIs
- Development seed data
- H2-based integration tests
- Windows startup command

## Scope boundary

This increment deliberately does not yet implement:

- CSV import APIs
- Sales-history persistence
- Purchase-order persistence
- Stockout and expiry risk calculation
- Database-backed dashboard aggregation
- Angular navigation and detail screens
- ML training

Those are the next Phase 2 increments.

## 1. Install PostgreSQL

Install PostgreSQL locally and make sure `psql` is available.

Create the local development role and database from the repository root:

```cmd
psql -U postgres -f scripts\postgres\setup_phase2_database.sql
```

Development defaults:

```text
Database: stockflow
User:     stockflow_app
Password: stockflow_dev
Port:     5432
```

The password is only a local-development default. Use environment-managed secrets outside local development.

## 2. Start the Phase 2 API

```cmd
run-core-api-phase2-windows.cmd
```

The script:

1. Configures Maven.
2. Runs the isolated backend tests.
3. Activates the `dev` profile.
4. Connects to PostgreSQL.
5. Applies Flyway migrations.
6. Loads development seed data.
7. Starts the API on port 8080.

The original `run-core-api-windows.cmd` remains usable for Sprint 1 compatibility. It uses the default `sprint1` profile with an in-memory H2 database. Use `run-core-api-phase2-windows.cmd` for the real PostgreSQL Phase 2 runtime.

## 3. Verify database health

```text
http://localhost:8080/actuator/health
http://localhost:8080/actuator/flyway
```

Expected health state:

```json
{
  "status": "UP"
}
```

## 4. Verify Phase 2 endpoints

All new business APIs require `X-Tenant-ID`.

### Foundation summary

```cmd
curl -H "X-Tenant-ID: TEN-ACME-PHARMA" http://localhost:8080/api/v1/foundation/summary
```

Expected counts from development seed data:

```json
{
  "warehouseCount": 3,
  "productCount": 1,
  "skuCount": 1,
  "batchCount": 3
}
```

### Warehouses

```cmd
curl -H "X-Tenant-ID: TEN-ACME-PHARMA" http://localhost:8080/api/v1/warehouses
```

### SKUs

```cmd
curl -H "X-Tenant-ID: TEN-ACME-PHARMA" http://localhost:8080/api/v1/skus
```

### Batch inventory

```cmd
curl -H "X-Tenant-ID: TEN-ACME-PHARMA" "http://localhost:8080/api/v1/inventory/batches?warehouseId=WH-CHENNAI&skuId=SKU-PARA-650"
```

The Chennai B2456 record should return:

```text
Available: 2,450
Reserved:     59
Blocked:       0
Usable:    2,391
```

## 5. Flyway migrations

```text
V001__create_tenants.sql
V002__create_warehouses.sql
V003__create_products_and_skus.sql
V004__create_batch_inventory.sql
```

Development-only seed data is isolated under:

```text
db/devdata/V005__seed_phase2_demo_data.sql
```

Production startup must not include the `dev` profile.

## 6. Tenant control

Every Phase 2 API requires:

```http
X-Tenant-ID: TEN-ACME-PHARMA
```

Repository methods always include `tenantId`. An unknown or inactive tenant is rejected with HTTP 403.

This is a prototype tenant control. A later security increment must derive the tenant from an authenticated identity and policy context rather than trusting the header by itself.

## 7. Tests

Run:

```cmd
cd services\stockflow-core-api
mvn -Dkotlin.compiler.daemon=false clean test
```

The tests use an isolated H2 database in PostgreSQL compatibility mode. They verify:

- Flyway migration execution
- Hibernate schema validation
- Tenant-scoped summary
- Batch usable-quantity calculation
- Mandatory tenant header
- Unknown-tenant rejection
- Existing dashboard compatibility

## 8. Next increment

Phase 2 Increment 2 should add:

1. Add `V006` migrations for import batch and import-error tables.
2. CSV upload and validation APIs.
3. Tenant, warehouse, SKU and batch-inventory importers.
4. File checksum and duplicate-import controls.
5. Row-level error reporting.
6. Import history screen contract.
