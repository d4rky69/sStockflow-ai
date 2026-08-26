# StockFlow AI Synthetic Data Assessment

## Decision

**Use the dataset, but do not import the raw archive directly into the Phase 2 database.**

The dataset is valuable for Phase 2 controlled-import development and later Phase 3 forecasting work because it contains:

- 3 tenants
- 10 warehouses
- 50 retailers
- 12 suppliers
- 50 products
- 100 SKUs
- 178,156 sales-history rows
- 103 current batch-inventory rows
- 503 inventory movements
- 103 purchase orders
- 201 dispatches
- 101 returns
- 7,600 daily weather rows
- Approximately two years of sales history, from 2024-06-01 through 2026-06-30

The uncompressed data is approximately 17 MB. It is large enough to exercise validation, import performance, demand aggregation and future model training without being too large for a developer workstation.

## Independent quality findings

The source validation report marks the archive as valid, but an independent cross-file review found issues that matter to the application schema.

### Blocking issues for direct foundation import

1. Six SKU rows refer to product IDs that are absent from `products.csv`:
   - `SKU-PARA-650`
   - `SKU-AMOX-500`
   - `SKU-MILK-1L`
   - `SKU-BACKPACK-01`
   - `SKU-ORS-PWD`
   - `SKU-INSU-GL`
2. `skus.csv` does not contain `tenant_id`; the application must derive it from the related product or use a prepared file that adds it.
3. Thirty-nine non-expiring SKUs have blank `default_shelf_life_days`.
4. Thirty-nine batch rows have blank `expiry_date`.
5. The original Phase 2 schema required both shelf life and expiry date, so it could not represent merchandise and other non-expiring products.
6. Twenty-four batch-inventory rows combine an Urban Trade warehouse/tenant with SKUs owned by the Pharma or FreshMart tenants.
7. Three hundred fourteen inventory-movement rows contain a SKU whose product tenant does not match the transaction tenant.
8. Sixty-one purchase-order rows contain a SKU whose product tenant does not match the purchase-order tenant.
9. Fifty-nine supplier-SKU rows and 59 lead-time-policy rows cross tenant boundaries through their SKU reference.

### Non-blocking quality observations

1. One purchase order is fully received but has status `PARTIALLY_RECEIVED`.
2. `promotion_id` is blank in all 178,156 sales rows, so promotion impact cannot yet be learned directly from the sales table.
3. The 503 movement rows do not contain source/destination warehouse values, so warehouse-transfer algorithms cannot be validated from movements yet.
4. Batch inventory has one snapshot date, 2026-07-01. It is useful for current-state risk calculations but not for historical inventory-level forecasting.
5. Purchase orders, returns and dispatches are concentrated in `TEN-ACME-PHARMA`, so those transaction types do not yet give balanced multi-tenant coverage.
6. The scenario configuration mentions a weather scenario, while the scenario-definition file contains a stockout scenario instead. Scenario metadata should be reconciled before automated scenario certification.
7. The expected-outcomes file does not contain an explicit expected outcome for `SCN-STOCKOUT-001`.

## What is useful now

### Phase 2

Use these files immediately:

- `reference/tenants.csv`
- `reference/warehouses.csv`
- `reference/products.csv`
- `reference/skus.csv`
- `transactions/batch_inventory.csv`

They support tenant, warehouse, product, SKU and current batch-inventory persistence.

### Later Phase 2 increments

Use these after their tables and APIs are implemented:

- Retailers
- Suppliers
- Supplier-SKU mappings
- Lead-time policies
- Warehouse routes
- Purchase orders
- Inventory movements
- Dispatches
- Returns

### Phase 3

Use these for forecasting and predictive work after feature-pipeline validation:

- Sales history
- Weather history
- Promotions
- Local events
- Demand signals

The sales data is useful as a baseline training dataset, but promotions must be linked to sales and model evaluation must be split chronologically to avoid leakage.

## Prepared import package

The project includes:

```text
data/import/StockFlow_AI_Synthetic_Foundation_Phase2_Ready.zip
```

The prepared package:

- Retains 3 tenants, 10 warehouses, 50 products, 100 SKUs and 79 valid batch rows
- Adds `tenant_id` to `skus.csv`
- Repairs the six unresolved SKU-to-product references using valid products from the same tenant and category
- Removes 24 cross-tenant batch rows rather than inventing warehouse or SKU ownership
- Preserves blank shelf-life and expiry values for non-expiring products
- Includes a machine-readable quality report with every repair and removed batch number

The original archive remains the source for future transaction and ML increments. The prepared package is specifically for Phase 2 Increment 2 foundation import.

## Final recommendation

Proceed with the dataset using the controlled-import framework. Do not use it as an unconditional Flyway seed and do not load all 178,156 sales rows into the current foundation tables. Validate first, import tenant by tenant, retain row-level errors and add transaction tables in subsequent increments.
