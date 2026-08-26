# Phase 2 Increment 2 — Controlled Synthetic Foundation Import

## Objective

Import tenant, warehouse, product, SKU and batch-inventory data from a validated ZIP package while retaining an auditable import job and row-level rejection evidence.

## Implemented capabilities

- ZIP upload endpoint
- Required-file validation
- Safe ZIP entry handling
- 30 MB expanded-package limit
- CSV header and row-width validation
- Tenant-scoped filtering
- Mandatory-field validation
- Boolean, numeric, date and timestamp validation
- Duplicate-key detection within each file
- Product, SKU and warehouse reference validation
- Quantity-allocation validation
- Manufacture/expiry-date validation
- `VALIDATE_ONLY` and `UPSERT` modes
- Strict all-or-nothing domain import
- Optional partial import using `strict=false`
- Import-job history
- Row-level import errors
- SHA-256 fingerprinting of the uploaded package
- Idempotent entity upsert
- Deterministic batch UUID generation
- Nullable shelf life and expiry support for non-expiring products

## Required ZIP files

The importer locates files by suffix, so the package can contain a parent folder.

```text
reference/tenants.csv
reference/warehouses.csv
reference/products.csv
reference/skus.csv
transactions/batch_inventory.csv
```

## APIs

### Validate

```http
POST /api/v1/imports/synthetic-foundation?mode=VALIDATE_ONLY&strict=true
X-Tenant-ID: TEN-ACME-PHARMA
Content-Type: multipart/form-data
```

Multipart field:

```text
file=<zip file>
```

### Import

```http
POST /api/v1/imports/synthetic-foundation?mode=UPSERT&strict=true
X-Tenant-ID: TEN-ACME-PHARMA
Content-Type: multipart/form-data
```

### Recent jobs

```http
GET /api/v1/imports
X-Tenant-ID: TEN-ACME-PHARMA
```

### Job result

```http
GET /api/v1/imports/{importJobId}
X-Tenant-ID: TEN-ACME-PHARMA
```

### Rejected rows

```http
GET /api/v1/imports/{importJobId}/errors
X-Tenant-ID: TEN-ACME-PHARMA
```

## Windows commands

Create the separate clean import database once:

```cmd
cd /d C:\Users\oveyj\Downloads\StockFlow_AI_Phase2_Increment2
"C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5433 -U postgres -d postgres -f scripts\postgres\setup_phase2_import_database.sql
```

Then start the clean Phase 2 profile, which runs only schema migrations and does not load the small V005 development seed:

```cmd
cd /d C:\Users\oveyj\Downloads\StockFlow_AI_Phase2_Increment2
set "STOCKFLOW_DB_URL=jdbc:postgresql://localhost:5433/stockflow_phase2"
set "STOCKFLOW_DB_USERNAME=stockflow_app"
set "STOCKFLOW_DB_PASSWORD=stockflow_dev"
call run-core-api-phase2-import-windows.cmd
```

Validate the prepared package:

```cmd
curl -X POST ^
  -H "X-Tenant-ID: TEN-ACME-PHARMA" ^
  -F "file=@data\import\StockFlow_AI_Synthetic_Foundation_Phase2_Ready.zip" ^
  "http://localhost:8080/api/v1/imports/synthetic-foundation?mode=VALIDATE_ONLY&strict=true"
```

After the result is `VALIDATED`, import it:

```cmd
curl -X POST ^
  -H "X-Tenant-ID: TEN-ACME-PHARMA" ^
  -F "file=@data\import\StockFlow_AI_Synthetic_Foundation_Phase2_Ready.zip" ^
  "http://localhost:8080/api/v1/imports/synthetic-foundation?mode=UPSERT&strict=true"
```

Repeat for:

```text
TEN-FRESH-MART
TEN-URBAN-TRADE
```

Each request imports only the tenant named in `X-Tenant-ID`; rows for other tenants are ignored and reported in `ignoredRows`.

## Expected tenant-level counts from the prepared package

| Tenant | Warehouses | Products | SKUs | Batch rows |
|---|---:|---:|---:|---:|
| TEN-ACME-PHARMA | 4 | 20 | 37 | 32 |
| TEN-FRESH-MART | 3 | 15 | 32 | 16 |
| TEN-URBAN-TRADE | 3 | 15 | 31 | 31 |

The total is 10 warehouses, 50 products, 100 SKUs and 79 valid batch rows. The preparation script removes 24 cross-tenant batch records from the raw source and records their batch numbers in `quality_report.json`.

## Import statuses

| Status | Meaning |
|---|---|
| `VALIDATED` | Validation-only request passed |
| `COMPLETED` | All valid data was upserted |
| `COMPLETED_WITH_ERRORS` | Partial import completed with `strict=false` |
| `REJECTED` | Validation failed or strict import contained rejected rows |
| `FAILED` | Package processing failed before row-level completion |

## Database changes

### V006

- Product criticality
- Shelf-life control flag
- Cold-chain-required flag
- SKU brand and pack size
- Nullable SKU shelf-life days
- Nullable batch expiry date

### V007

- `import_job`
- `import_error`

## Scope boundary

This increment intentionally does not import sales history, purchase orders, returns, movements, dispatches, weather or promotions. Those files remain valuable and will be introduced with their own schemas, validations and performance controls in later increments.
