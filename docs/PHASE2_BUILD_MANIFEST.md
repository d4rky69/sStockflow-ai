# Phase 2 Build Manifest

## Increment 1 — Persistent Database Foundation

Implemented:

- PostgreSQL datasource profiles
- Flyway schema migrations
- Tenant, warehouse, product, SKU and batch-inventory entities
- Spring Data repositories
- Tenant-scoped foundation read APIs
- Structured API errors
- Development seed dataset
- H2 integration-test profile
- Foundation API integration tests
- PostgreSQL setup script
- Windows Phase 2 launcher

## Increment 2 — Controlled Synthetic Foundation Import

Implemented:

- Independent synthetic-data quality assessment
- Phase 2-ready foundation import package
- Schema alignment for non-expiring products
- Product criticality and cold-chain fields
- SKU brand and pack-size fields
- Import job and import error persistence
- ZIP safety and size controls
- CSV parser with quoted-field support
- Required-file and header validation
- Tenant-scoped filtering
- Cross-reference validation
- Duplicate detection
- `VALIDATE_ONLY` and `UPSERT` modes
- Strict and partial-import modes
- SHA-256 package fingerprinting
- Import history and error APIs
- Integration-test coverage for valid and invalid packages
- Clean `phase2` PostgreSQL profile without development seed data
- Windows controlled-import launcher
- OpenAPI import contracts

## Import endpoints

```http
POST /api/v1/imports/synthetic-foundation
GET  /api/v1/imports
GET  /api/v1/imports/{importJobId}
GET  /api/v1/imports/{importJobId}/errors
```

## Foundation endpoints

```http
GET /api/v1/foundation/summary
GET /api/v1/warehouses
GET /api/v1/warehouses/{warehouseId}
GET /api/v1/skus
GET /api/v1/inventory/batches
```

All tenant-scoped endpoints require:

```http
X-Tenant-ID: <tenant-id>
```

## Validation performed while preparing Increment 2

- Raw archive extracted and all 21 CSV files inspected
- Independent row counts matched the source generation report
- Primary-key and major foreign-key checks performed
- Six invalid SKU-to-product references identified
- Prepared foundation package repaired and revalidated
- Prepared package contains no unresolved product, SKU or warehouse references
- Twenty-four cross-tenant batch rows were excluded and recorded in the quality report
- Repository structural validator passed
- POM XML parsed successfully
- YAML and OpenAPI files parsed successfully
- Pure Kotlin CSV parser compiled successfully
- Import service compiled against API-compatible local stubs for syntax and type validation

## Developer validation still required

Run the real Maven test suite on the developer workstation:

```cmd
cd services\stockflow-core-api
mvn -Dkotlin.compiler.daemon=false clean test
```

Then start PostgreSQL mode:

```cmd
run-core-api-phase2-import-windows.cmd
```

Validate the supplied package before importing it. See `PHASE2_INCREMENT2_CONTROLLED_IMPORT.md`.
