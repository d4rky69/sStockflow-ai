# StockFlow AI — Phase 2 Increment 3

## Sales History and Database-Backed Analytics

This increment adds controlled retailer and sales-history import to the Phase 2 database and exposes tenant-scoped sales analytics.

## New database tables

- `retailer`
- `sales_history`

## Import endpoint

```http
POST /api/v1/imports/synthetic-sales?mode=VALIDATE_ONLY&strict=true
POST /api/v1/imports/synthetic-sales?mode=UPSERT&strict=true
X-Tenant-ID: <tenant-id>
Content-Type: multipart/form-data
```

The package must contain:

```text
data/synthetic/reference/retailers.csv
data/synthetic/transactions/sales_history.csv
```

## Analytics endpoints

```http
GET /api/v1/analytics/sales/summary
GET /api/v1/analytics/sales/top-skus?limit=10
```

Both endpoints accept optional `dateFrom` and `dateTo` query parameters in `yyyy-MM-dd` format.

## Prepared dataset counts

| Tenant | Retailers | Sales rows | Accepted rows |
|---|---:|---:|---:|
| TEN-ACME-PHARMA | 18 | 75,221 | 75,239 |
| TEN-FRESH-MART | 16 | 84,835 | 84,851 |
| TEN-URBAN-TRADE | 16 | 18,100 | 18,116 |
| Total | 50 | 178,156 | 178,206 |

The import is idempotent because every sales row uses a deterministic UUID derived from its tenant/date/warehouse/retailer/SKU natural key.

## Recommended sequence

1. Apply the Increment 3 patch.
2. Run `mvn clean test`.
3. Restart the API so Flyway applies `V008`.
4. Validate the prepared sales package tenant by tenant.
5. Run UPSERT tenant by tenant.
6. Verify database counts and analytics APIs.
