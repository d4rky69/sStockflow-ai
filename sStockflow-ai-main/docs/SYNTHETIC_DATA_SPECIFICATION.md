# StockFlow AI — Synthetic Data Specification

**Document type:** Data specification and implementation guide  
**Project:** StockFlow AI  
**Purpose:** Define how synthetic wholesale and distribution data must be created, formatted, validated, stored and used for the SIH prototype  
**Target stack:** Angular UI, Kotlin + Spring Boot core API, Python forecasting and optimisation services, PostgreSQL, Redis and MCP servers  
**Primary exchange format for MVP:** UTF-8 CSV  
**Production target:** PostgreSQL tables populated through validated import APIs

---

## 1. Why Synthetic Data Is Required

The SIH prototype needs realistic data for demonstrating:

- SKU-level demand forecasting
- Warehouse-level inventory visibility
- Stockout prediction
- Near-expiry detection
- Excess and slow-moving inventory detection
- FEFO/FIFO allocation
- Inter-warehouse stock rebalancing
- Duplicate purchase-order detection
- Purchase-versus-transfer comparison
- Working-capital calculations
- Explainable recommendations
- MCP tool execution and audit

Synthetic data should imitate real distributor operations without using confidential customer, patient, retailer or supplier records.

The prototype should initially model:

- 3 distributors or business units
- 10 warehouses
- 50 retailers
- 100 SKUs
- Multiple batches and expiry dates
- 90 to 365 days of sales history
- Open purchase orders
- Supplier lead times
- Promotions and local events
- At least one weather-driven demand event
- At least one sudden demand surge
- Overstock, stockout and expiry-risk scenarios

---

## 2. Recommended Data Approach

Use a **layered synthetic dataset** rather than one large CSV file.

```text
Reference data
    ↓
Transactional data
    ↓
External signals
    ↓
Scenario injections
    ↓
Expected outcomes / labels
```

### 2.1 Reference data

Stable business entities:

- Tenants/distributors
- Warehouses
- Products and SKUs
- Suppliers
- Retailers
- Routes and lead times

### 2.2 Transactional data

Time-dependent business activity:

- Daily sales
- Orders
- Dispatches
- Returns
- Inventory balances
- Batch inventory
- Purchase orders
- Inventory movements

### 2.3 External signals

Variables that influence demand:

- Promotions
- Weather
- Festivals and local events
- Disease or health-demand indicators
- Holidays

### 2.4 Scenario injections

Deliberately created events used to demonstrate AI value:

- Demand surge
- Stockout risk
- Near-expiry risk
- Excess stock
- Duplicate purchase order
- Abnormal return
- Unrecorded movement

### 2.5 Expected outcomes

Ground-truth records used to test whether the system detects the intended scenario correctly.

---

## 3. File and Folder Structure

```text
data/
├── synthetic/
│   ├── manifest.json
│   ├── reference/
│   │   ├── tenants.csv
│   │   ├── warehouses.csv
│   │   ├── retailers.csv
│   │   ├── suppliers.csv
│   │   ├── products.csv
│   │   ├── skus.csv
│   │   ├── supplier_sku.csv
│   │   ├── lead_time_policies.csv
│   │   └── warehouse_routes.csv
│   ├── transactions/
│   │   ├── sales_history.csv
│   │   ├── batch_inventory.csv
│   │   ├── inventory_movements.csv
│   │   ├── open_purchase_orders.csv
│   │   ├── dispatches.csv
│   │   └── returns.csv
│   ├── signals/
│   │   ├── promotions.csv
│   │   ├── weather_daily.csv
│   │   ├── local_events.csv
│   │   └── demand_signals.csv
│   ├── scenarios/
│   │   ├── scenario_definitions.csv
│   │   └── expected_outcomes.csv
│   └── generated/
│       ├── generation_report.json
│       └── validation_report.json
└── seed/
    └── generator_config.yaml
```

---

## 4. General Formatting Standard

All CSV files should follow the same conventions.

| Rule | Required format |
|---|---|
| Encoding | UTF-8 |
| Delimiter | Comma `,` |
| Header | Required on first row |
| Date | `YYYY-MM-DD` |
| Timestamp | ISO 8601, preferably UTC: `YYYY-MM-DDTHH:MM:SSZ` |
| Decimal separator | Period `.` |
| Currency values | Numeric only; do not include `₹`, commas or text |
| Currency code | ISO-style three-letter code such as `INR` |
| Boolean | `true` or `false` |
| Missing value | Empty field, not `NA`, `-`, `null` or `0` |
| ID case | Uppercase stable business IDs |
| Text quoting | Quote fields containing commas, quotes or new lines |
| Quantity | Decimal number; normally non-negative |
| Percentage | Decimal fraction; `0.15` means 15% |
| File naming | Lowercase `snake_case.csv` |
| Column naming | Lowercase `snake_case` |

### 4.1 Correct examples

```csv
sku_id,unit_cost,currency,active
SKU-PARA-650,2.75,INR,true
```

### 4.2 Incorrect examples

```csv
SKU ID,Unit Cost,Currency,Active
Para650,"₹2,75",Rupees,Yes
```

---

## 5. Identifier Standard

Use readable, stable IDs so that developers and judges can understand the demo.

| Entity | Pattern | Example |
|---|---|---|
| Tenant | `TEN-<CODE>` | `TEN-ACME-PHARMA` |
| Warehouse | `WH-<CITY>` | `WH-CHENNAI` |
| Retailer | `RET-<REGION>-<NUMBER>` | `RET-SOUTH-001` |
| Supplier | `SUP-<NUMBER>` | `SUP-001` |
| Product | `PRD-<CATEGORY>-<NUMBER>` | `PRD-MED-001` |
| SKU | `SKU-<SHORT-NAME>` | `SKU-PARA-650` |
| Batch | `BAT-<SKU>-<NUMBER>` | `BAT-PARA-650-2456` |
| Purchase order | `PO-<YEAR>-<NUMBER>` | `PO-2026-007823` |
| Movement | `MOV-<DATE>-<NUMBER>` | `MOV-20260726-001` |
| Promotion | `PROMO-<NUMBER>` | `PROMO-001` |
| Scenario | `SCN-<TYPE>-<NUMBER>` | `SCN-EXPIRY-001` |

Do not regenerate IDs randomly on every run. A fixed random seed must reproduce the same dataset and IDs.

---

## 6. Dataset Manifest

Create `manifest.json` to describe the dataset.

```json
{
  "dataset_name": "stockflow-sih-demo-v1",
  "dataset_version": "1.0.0",
  "generated_at": "2026-07-26T10:00:00Z",
  "random_seed": 20260726,
  "currency": "INR",
  "timezone": "Asia/Kolkata",
  "history_start_date": "2025-08-01",
  "history_end_date": "2026-07-25",
  "forecast_as_of_date": "2026-07-26",
  "tenant_count": 3,
  "warehouse_count": 10,
  "retailer_count": 50,
  "sku_count": 100,
  "verticals": ["PHARMA", "SUPERMARKET", "MERCHANDISE"],
  "files": [
    "reference/warehouses.csv",
    "reference/skus.csv",
    "transactions/sales_history.csv",
    "transactions/batch_inventory.csv"
  ]
}
```

The API and AI services should read the same `forecast_as_of_date`. Avoid using each developer's current system date, because expiry and stockout results will otherwise change unpredictably.

---

# Part I — Reference Data Files

## 7. `tenants.csv`

### Purpose

Represents each distributor or independent SaaS tenant.

### Columns

| Column | Type | Required | Description |
|---|---|---:|---|
| `tenant_id` | string | Yes | Stable tenant identifier |
| `tenant_name` | string | Yes | Distributor name |
| `vertical` | enum | Yes | `PHARMA`, `SUPERMARKET`, `MERCHANDISE` |
| `currency` | string | Yes | Normally `INR` |
| `timezone` | string | Yes | Example: `Asia/Kolkata` |
| `active` | boolean | Yes | Tenant status |

### Example

```csv
tenant_id,tenant_name,vertical,currency,timezone,active
TEN-ACME-PHARMA,Acme Pharma Distribution,PHARMA,INR,Asia/Kolkata,true
TEN-FRESH-MART,FreshMart Wholesale,SUPERMARKET,INR,Asia/Kolkata,true
TEN-URBAN-TRADE,Urban Trade Distribution,MERCHANDISE,INR,Asia/Kolkata,true
```

---

## 8. `warehouses.csv`

### Columns

| Column | Type | Required | Description |
|---|---|---:|---|
| `warehouse_id` | string | Yes | Warehouse ID |
| `tenant_id` | string | Yes | Owning tenant |
| `warehouse_name` | string | Yes | Display name |
| `city` | string | Yes | City |
| `state` | string | Yes | State |
| `latitude` | decimal | Yes | Route and distance calculations |
| `longitude` | decimal | Yes | Route and distance calculations |
| `capacity_units` | decimal | Yes | Maximum storage capacity |
| `cold_chain_available` | boolean | Yes | Whether cold-chain stock is supported |
| `active` | boolean | Yes | Warehouse status |

### Example

```csv
warehouse_id,tenant_id,warehouse_name,city,state,latitude,longitude,capacity_units,cold_chain_available,active
WH-CHENNAI,TEN-ACME-PHARMA,Chennai Central Warehouse,Chennai,Tamil Nadu,13.0827,80.2707,500000,true,true
WH-BENGALURU,TEN-ACME-PHARMA,Bengaluru Regional Warehouse,Bengaluru,Karnataka,12.9716,77.5946,350000,true,true
WH-HYDERABAD,TEN-ACME-PHARMA,Hyderabad Regional Warehouse,Hyderabad,Telangana,17.3850,78.4867,300000,true,true
```

---

## 9. `retailers.csv`

### Columns

| Column | Type | Required | Description |
|---|---|---:|---|
| `retailer_id` | string | Yes | Retailer identifier |
| `tenant_id` | string | Yes | Distributor tenant |
| `retailer_name` | string | Yes | Pharmacy/store name |
| `retailer_type` | enum | Yes | `PHARMACY`, `SUPERMARKET`, `GROCERY`, `GENERAL_STORE` |
| `warehouse_id` | string | Yes | Primary servicing warehouse |
| `city` | string | Yes | Retailer city |
| `region` | string | Yes | Sales territory |
| `credit_days` | integer | No | Payment terms |
| `active` | boolean | Yes | Retailer status |

### Example

```csv
retailer_id,tenant_id,retailer_name,retailer_type,warehouse_id,city,region,credit_days,active
RET-SOUTH-001,TEN-ACME-PHARMA,HealthPlus Pharmacy,PHARMACY,WH-CHENNAI,Chennai,SOUTH,30,true
RET-SOUTH-002,TEN-ACME-PHARMA,CareMed Pharmacy,PHARMACY,WH-BENGALURU,Bengaluru,SOUTH,45,true
```

---

## 10. `suppliers.csv`

### Columns

| Column | Type | Required | Description |
|---|---|---:|---|
| `supplier_id` | string | Yes | Supplier identifier |
| `tenant_id` | string | Yes | Tenant |
| `supplier_name` | string | Yes | Supplier name |
| `city` | string | No | Supplier location |
| `default_lead_time_days` | integer | Yes | Normal supply lead time |
| `reliability_score` | decimal | Yes | Range `0.00` to `1.00` |
| `active` | boolean | Yes | Supplier status |

---

## 11. `products.csv`

A product is the commercial product family. A SKU is the exact sellable unit or variant.

### Columns

| Column | Type | Required | Description |
|---|---|---:|---|
| `product_id` | string | Yes | Product family ID |
| `tenant_id` | string | Yes | Tenant |
| `product_name` | string | Yes | Product name |
| `category` | string | Yes | Medicine, dairy, beverage, school supplies, etc. |
| `vertical` | enum | Yes | Business vertical |
| `criticality` | enum | Yes | `LOW`, `MEDIUM`, `HIGH`, `LIFE_SAVING` |
| `shelf_life_controlled` | boolean | Yes | Whether expiry is applicable |
| `cold_chain_required` | boolean | Yes | Whether cold chain is required |
| `active` | boolean | Yes | Status |

---

## 12. `skus.csv`

### Columns

| Column | Type | Required | Description |
|---|---|---:|---|
| `sku_id` | string | Yes | SKU identifier |
| `product_id` | string | Yes | Parent product |
| `sku_name` | string | Yes | Full sellable SKU name |
| `brand` | string | No | Brand |
| `pack_size` | string | Yes | Example `10 TABLETS`, `1 LITRE` |
| `base_uom` | string | Yes | `UNIT`, `BOX`, `BOTTLE`, etc. |
| `unit_cost` | decimal | Yes | Purchase cost per base UOM |
| `selling_price` | decimal | Yes | Normal selling price |
| `currency` | string | Yes | `INR` |
| `minimum_safety_stock` | decimal | Yes | Minimum warehouse safety stock |
| `reorder_multiple` | decimal | Yes | Order rounding quantity |
| `default_shelf_life_days` | integer | No | Empty for non-expiring products |
| `fefo_required` | boolean | Yes | FEFO dispatch rule |
| `active` | boolean | Yes | Status |

### Example

```csv
sku_id,product_id,sku_name,brand,pack_size,base_uom,unit_cost,selling_price,currency,minimum_safety_stock,reorder_multiple,default_shelf_life_days,fefo_required,active
SKU-PARA-650,PRD-MED-001,Paracetamol 650mg Tablet,MedSure,10 TABLETS,STRIP,18.50,25.00,INR,500,100,730,true,true
SKU-AMOX-500,PRD-MED-002,Amoxicillin 500mg Capsule,HealthGen,10 CAPSULES,STRIP,42.00,60.00,INR,300,50,540,true,true
SKU-MILK-1L,PRD-DAIRY-001,Toned Milk 1 Litre,FreshDay,1 LITRE,PACK,42.00,52.00,INR,100,20,5,true,true
SKU-BACKPACK-01,PRD-MERCH-001,School Backpack 25L,UrbanCarry,25 LITRE,UNIT,450.00,799.00,INR,25,5,,false,true
```

---

## 13. `supplier_sku.csv`

### Columns

```text
supplier_id
sku_id
purchase_price
minimum_order_quantity
order_multiple
lead_time_days
supplier_priority
active
```

This file allows the procurement engine to compare alternate suppliers and landed cost.

---

## 14. `lead_time_policies.csv`

### Columns

```text
lead_time_policy_id
tenant_id
supplier_id
warehouse_id
sku_id
normal_lead_time_days
minimum_lead_time_days
maximum_lead_time_days
lead_time_variability_days
```

Use variability so that every order does not arrive exactly on the same fixed day.

---

## 15. `warehouse_routes.csv`

### Columns

| Column | Type | Description |
|---|---|---|
| `source_warehouse_id` | string | Origin |
| `destination_warehouse_id` | string | Destination |
| `distance_km` | decimal | Road distance |
| `travel_time_hours` | decimal | Typical travel duration |
| `fixed_transfer_cost` | decimal | Fixed trip cost |
| `cost_per_unit` | decimal | Variable cost per unit |
| `cold_chain_supported` | boolean | Cold-chain transport capability |
| `active` | boolean | Route status |

### Example

```csv
source_warehouse_id,destination_warehouse_id,distance_km,travel_time_hours,fixed_transfer_cost,cost_per_unit,cold_chain_supported,active
WH-CHENNAI,WH-BENGALURU,346,7.5,12000,0.60,true,true
WH-CHENNAI,WH-HYDERABAD,627,11.5,18000,0.75,true,true
```

---

# Part II — Transaction Data Files

## 16. `sales_history.csv`

This is the most important forecasting file.

### Recommended grain

> One row per `tenant_id + date + warehouse_id + retailer_id + sku_id`.

For a smaller MVP, `retailer_id` may be empty and the grain can be warehouse-SKU-day.

### Columns

| Column | Type | Required | Description |
|---|---|---:|---|
| `sales_date` | date | Yes | Transaction day |
| `tenant_id` | string | Yes | Tenant |
| `warehouse_id` | string | Yes | Servicing warehouse |
| `retailer_id` | string | No | Retailer |
| `sku_id` | string | Yes | SKU |
| `ordered_quantity` | decimal | Yes | Quantity requested |
| `fulfilled_quantity` | decimal | Yes | Quantity supplied |
| `sales_quantity` | decimal | Yes | Final sale quantity |
| `return_quantity` | decimal | Yes | Returned quantity |
| `lost_sales_quantity` | decimal | Yes | Estimated unmet demand |
| `unit_selling_price` | decimal | Yes | Selling price |
| `promotion_id` | string | No | Active promotion |
| `stockout_flag` | boolean | Yes | Whether inventory constrained sales |

### Example

```csv
sales_date,tenant_id,warehouse_id,retailer_id,sku_id,ordered_quantity,fulfilled_quantity,sales_quantity,return_quantity,lost_sales_quantity,unit_selling_price,promotion_id,stockout_flag
2026-07-20,TEN-ACME-PHARMA,WH-CHENNAI,RET-SOUTH-001,SKU-PARA-650,85,85,82,3,0,25.00,,false
2026-07-21,TEN-ACME-PHARMA,WH-BENGALURU,RET-SOUTH-002,SKU-PARA-650,140,90,88,2,50,25.00,,true
```

### Important forecasting rule

Do not treat low sales during a stockout as low demand.

A useful synthetic demand field is:

```text
estimated_true_demand = sales_quantity + lost_sales_quantity
```

The production model may calculate this internally rather than storing it.

---

## 17. `batch_inventory.csv`

### Recommended grain

> One row per `tenant_id + warehouse_id + sku_id + batch_number`.

### Columns

| Column | Type | Required | Description |
|---|---|---:|---|
| `snapshot_date` | date | Yes | Inventory as-of date |
| `tenant_id` | string | Yes | Tenant |
| `warehouse_id` | string | Yes | Warehouse |
| `sku_id` | string | Yes | SKU |
| `batch_number` | string | Yes | Batch/lot identifier |
| `manufacture_date` | date | No | Manufacturing date |
| `expiry_date` | date | No | Expiry date |
| `available_quantity` | decimal | Yes | Free stock |
| `reserved_quantity` | decimal | Yes | Allocated stock |
| `blocked_quantity` | decimal | Yes | Quality or compliance blocked stock |
| `unit_cost` | decimal | Yes | Batch unit cost |
| `currency` | string | Yes | Currency |
| `storage_condition_code` | string | No | `AMBIENT`, `CHILLED`, `FROZEN` |
| `last_movement_at` | timestamp | No | Most recent movement |

### Example

```csv
snapshot_date,tenant_id,warehouse_id,sku_id,batch_number,manufacture_date,expiry_date,available_quantity,reserved_quantity,blocked_quantity,unit_cost,currency,storage_condition_code,last_movement_at
2026-07-26,TEN-ACME-PHARMA,WH-CHENNAI,SKU-PARA-650,B2456,2025-09-01,2026-09-09,2450,100,0,18.50,INR,AMBIENT,2026-07-24T16:20:00Z
2026-07-26,TEN-ACME-PHARMA,WH-BENGALURU,SKU-PARA-650,B2512,2026-01-10,2027-01-10,250,50,0,18.75,INR,AMBIENT,2026-07-25T10:05:00Z
```

### Derived values

```text
physical_quantity = available_quantity + reserved_quantity + blocked_quantity
usable_quantity = available_quantity
inventory_value = available_quantity × unit_cost
days_to_expiry = expiry_date - forecast_as_of_date
```

---

## 18. `inventory_movements.csv`

### Columns

```text
movement_id
movement_timestamp
tenant_id
warehouse_id
sku_id
batch_number
movement_type
quantity
reference_type
reference_id
source_warehouse_id
destination_warehouse_id
user_id
recorded_flag
```

### Allowed `movement_type`

```text
RECEIPT
SALE_ISSUE
TRANSFER_OUT
TRANSFER_IN
RETURN_IN
RETURN_TO_SUPPLIER
ADJUSTMENT_IN
ADJUSTMENT_OUT
EXPIRY_WRITE_OFF
DAMAGE_WRITE_OFF
```

To create an unrecorded-movement scenario, reduce the inventory snapshot without generating the expected matching movement, and label the anomaly in `expected_outcomes.csv`.

---

## 19. `open_purchase_orders.csv`

### Columns

| Column | Type | Description |
|---|---|---|
| `purchase_order_id` | string | PO number |
| `tenant_id` | string | Tenant |
| `supplier_id` | string | Supplier |
| `warehouse_id` | string | Delivery warehouse |
| `sku_id` | string | SKU |
| `order_date` | date | PO date |
| `expected_delivery_date` | date | Expected receipt |
| `ordered_quantity` | decimal | Ordered quantity |
| `received_quantity` | decimal | Quantity already received |
| `open_quantity` | decimal | Remaining quantity |
| `unit_purchase_price` | decimal | Purchase price |
| `currency` | string | Currency |
| `status` | enum | `OPEN`, `PARTIALLY_RECEIVED`, `DELAYED`, `CANCELLED` |
| `buyer_id` | string | Buyer/user |

### Duplicate PO scenario

Two POs are considered possible duplicates when they have substantially the same:

- Tenant
- Warehouse
- SKU
- Supplier
- Order date window
- Required delivery window
- Quantity or overlapping demand coverage

Do not make every duplicate exactly identical. Include near-duplicates to test the detection logic.

---

## 20. `dispatches.csv`

Recommended columns:

```text
dispatch_id
dispatch_date
tenant_id
warehouse_id
retailer_id
sku_id
batch_number
quantity
delivery_date
vehicle_id
delivery_status
```

---

## 21. `returns.csv`

Recommended columns:

```text
return_id
return_date
tenant_id
retailer_id
warehouse_id
sku_id
batch_number
quantity
return_reason
condition
credit_value
currency
```

Allowed reasons can include:

```text
DAMAGED
EXPIRED
NEAR_EXPIRY
WRONG_PRODUCT
QUALITY_ISSUE
UNSOLD_STOCK
CUSTOMER_RETURN
```

---

# Part III — External Signal Files

## 22. `promotions.csv`

### Columns

```text
promotion_id
tenant_id
sku_id
region
start_date
end_date
promotion_type
discount_percentage
expected_demand_multiplier
```

### Example

```csv
promotion_id,tenant_id,sku_id,region,start_date,end_date,promotion_type,discount_percentage,expected_demand_multiplier
PROMO-001,TEN-FRESH-MART,SKU-JUICE-1L,SOUTH,2026-05-01,2026-05-15,PRICE_DISCOUNT,0.10,1.25
```

---

## 23. `weather_daily.csv`

### Columns

```text
weather_date
city
maximum_temperature_c
minimum_temperature_c
rainfall_mm
humidity_percentage
weather_condition
heatwave_flag
heavy_rain_flag
```

Weather should only materially affect relevant categories. For example:

- Heat increases beverage and ice-cream demand.
- Heavy rain may increase delivery delays.
- Extreme heat may influence ORS demand.

Do not apply the same multiplier to every SKU.

---

## 24. `local_events.csv`

### Columns

```text
event_id
event_name
city
region
start_date
end_date
event_type
expected_footfall
impact_category
expected_demand_multiplier
```

Possible event types:

```text
FESTIVAL
SPORTS
SCHOOL_OPENING
PUBLIC_HEALTH_CAMPAIGN
LOCAL_FAIR
ELECTION
CONCERT
```

---

## 25. `demand_signals.csv`

Use this for vertical-specific signals that do not belong in promotions or weather.

### Columns

```text
signal_id
signal_date
region
signal_type
signal_value
signal_unit
sku_id
category
expected_effect
```

Examples:

- Disease outbreak index
- Vaccination campaign
- Competitor stockout
- New-product launch
- Regional demand index

---

# Part IV — Scenario Design

## 26. Scenario Definition Format

Create `scenario_definitions.csv`.

```csv
scenario_id,scenario_type,description,start_date,end_date,tenant_id,warehouse_id,sku_id,severity
SCN-EXPIRY-001,NEAR_EXPIRY,Paracetamol excess stock in Chennai with demand shortage in Bengaluru,2026-07-26,2026-09-09,TEN-ACME-PHARMA,WH-CHENNAI,SKU-PARA-650,HIGH
```

Allowed scenario types:

```text
DEMAND_SURGE
STOCKOUT_RISK
NEAR_EXPIRY
EXCESS_INVENTORY
SLOW_MOVING
DUPLICATE_PO
ABNORMAL_RETURN
UNRECORDED_MOVEMENT
SUPPLIER_DELAY
PROMOTION_LIFT
WEATHER_LIFT
```

---

## 27. Expected Outcome Format

Create `expected_outcomes.csv` to test detection accuracy.

```csv
scenario_id,expected_risk_type,expected_source_warehouse_id,expected_destination_warehouse_id,expected_min_quantity,expected_max_quantity,expected_action,expected_priority
SCN-EXPIRY-001,NEAR_EXPIRY,WH-CHENNAI,WH-BENGALURU,800,1000,TRANSFER,HIGH
```

This file should never be supplied as a model input. It is test ground truth only.

---

## 28. Primary Pharmaceutical Demo Scenario

Use the following judge-facing scenario.

### Chennai

```text
SKU: SKU-PARA-650
Batch: B2456
Available quantity: 2,450 units
Expiry: 45 days from the forecast as-of date
Expected local consumption before expiry: approximately 700 units
```

### Bengaluru

```text
Expected stockout: within 8 days
Forecast demand: approximately 1,200 units
```

### Hyderabad

```text
Forecast demand: approximately 650 units
```

### Expected recommendation

```text
Transfer approximately 900 units to Bengaluru.
Transfer approximately 600 units to Hyderabad.
Retain sufficient safety stock in Chennai.
Avoid or postpone an unnecessary open purchase order.
```

### Required explanation

The recommendation should state:

- Facts retrieved from inventory and purchase orders
- Forecast demand and confidence
- Expiry risk
- Safety-stock check
- Batch selected through FEFO
- Transport cost
- Expected expiry loss avoided
- Expected stockout loss avoided
- Net financial benefit
- Required approval status

---

## 29. Supermarket Demo Scenario

Create a heatwave-driven demand lift.

Example:

```text
SKU: SKU-JUICE-1L
Affected warehouses: Chennai and Hyderabad
Weather: maximum temperature above 40°C for 5 days
Historical effect: beverage demand multiplier between 1.20 and 1.45
Current Chennai stock: insufficient for forecast
Current Bengaluru stock: excess cover above 45 days
Expected action: transfer stock before creating a new PO
```

---

## 30. Merchandise Demo Scenario

Create seasonal overstock.

Example:

```text
SKU: SKU-BACKPACK-01
Source warehouse: Chennai
Source cover: 120 days
Destination warehouse: Bengaluru
Destination forecast: school-opening demand surge
Expected comparison: transfer cost versus markdown loss
Expected action: transfer when net benefit is positive
```

---

# Part V — Synthetic Generation Rules

## 31. Demand Generation Formula

A practical synthetic daily-demand formula is:

```text
base_demand
× weekday_factor
× seasonality_factor
× promotion_factor
× weather_factor
× event_factor
× retailer_factor
× random_noise
```

Suggested interpretation:

```text
base_demand          Average daily demand for the SKU-location pair
weekday_factor       Day-of-week behaviour
seasonality_factor   Monthly or festival seasonality
promotion_factor     Promotion uplift
weather_factor       Category-specific weather response
event_factor         Local event impact
retailer_factor      Retailer size and buying pattern
random_noise         Controlled variability
```

### Important constraints

- Demand cannot be negative.
- Use intermittent zero-demand days for slow-moving SKUs.
- Do not use identical seasonal patterns for every SKU.
- Do not generate perfectly smooth data.
- Avoid unrealistically extreme noise unless creating an anomaly.
- Preserve a separate latent `true_demand` during generation.
- Observed sales may be lower than true demand when a stockout occurs.

---

## 32. SKU Demand Profiles

Assign every SKU a demand profile.

| Profile | Characteristics | Example |
|---|---|---|
| `STABLE` | Low volatility, consistent demand | Common medicines |
| `SEASONAL` | Strong monthly or festival pattern | School supplies |
| `PROMOTION_SENSITIVE` | Large promotion uplift | FMCG |
| `WEATHER_SENSITIVE` | Heat/rain response | Beverages, ORS |
| `INTERMITTENT` | Many zero-demand days | Specialty medicine |
| `NEW_PRODUCT` | Limited history and ramp-up | New merchandise |
| `DECLINING` | Reducing demand | End-of-life product |
| `ERRATIC` | High variability | Fashion/seasonal goods |

Add `demand_profile` to `skus.csv` or keep it in the generator configuration.

---

## 33. Suggested Vertical Distribution

For a 100-SKU SIH dataset, a practical suggested split is:

```text
40 pharmaceutical SKUs
35 supermarket/grocery SKUs
25 merchandise/FMCG SKUs
```

This is a recommended prototype distribution, not a mandatory business rule.

Ensure all three verticals include:

- Fast-moving SKUs
- Slow-moving SKUs
- At least one stockout-risk SKU
- At least one excess-stock SKU
- At least one anomaly scenario

Only expiry-controlled verticals need batch expiry.

---

## 34. Inventory Generation Rules

Generate opening inventory from forecast cover.

```text
opening_stock = expected_daily_demand × target_days_of_cover
```

Typical synthetic cover ranges:

| Condition | Days of cover |
|---|---:|
| Stockout risk | 0–7 |
| Healthy | 15–45 |
| Excess | 60–120 |
| Severe overstock | Above 120 |

For expiring products, split stock across multiple batches with different expiry dates. Do not put all stock in one batch.

---

## 35. Expiry Generation Rules

For shelf-life-controlled products:

```text
expiry_date = manufacture_date + shelf_life_days
```

Create a controlled mix:

```text
5–10% near expiry
1–3% already expired for data-quality testing
15–25% medium shelf life
remaining stock with healthy shelf life
```

Already-expired stock must be blocked or separately labelled; it must never be included as usable transfer inventory.

---

## 36. Purchase Order Generation Rules

For each warehouse-SKU pair:

1. Calculate the synthetic reorder point.
2. Generate POs when inventory falls below that point.
3. Add lead-time variability.
4. Create a small number of delayed POs.
5. Create deliberate duplicate or unnecessary POs for test scenarios.
6. Ensure PO quantities follow `reorder_multiple`.

---

## 37. Return Generation Rules

Normal returns should be low and category-dependent.

Example synthetic ranges:

```text
Pharma: 0.5%–2.0% of dispatch quantity
Grocery: 1.0%–4.0%
Merchandise: 2.0%–8.0%
```

Create abnormal-return scenarios by increasing returns sharply for one retailer, SKU or batch.

---

## 38. Random Seed and Reproducibility

Every generator run must accept a seed.

Example:

```yaml
random_seed: 20260726
```

The same configuration and seed must produce:

- The same IDs
- The same dates
- The same quantities
- The same scenarios
- The same expected outcomes

This allows QA, backend, AI and MCP teams to reproduce defects.

---

# Part VI — Validation Rules

## 39. Referential Integrity

Before importing data, validate:

- Every `tenant_id` exists.
- Every `warehouse_id` belongs to the stated tenant.
- Every `sku_id` exists and belongs to the tenant.
- Every `retailer_id` belongs to the tenant.
- Every `supplier_id` belongs to the tenant.
- Every batch references a valid SKU and warehouse.
- Every purchase order references a valid supplier, warehouse and SKU.
- No record references another tenant's entity.

---

## 40. Business Validation Rules

### Quantities

```text
available_quantity >= 0
reserved_quantity >= 0
blocked_quantity >= 0
fulfilled_quantity <= ordered_quantity
sales_quantity <= fulfilled_quantity
received_quantity <= ordered_quantity
open_quantity = ordered_quantity - received_quantity
```

### Dates

```text
manufacture_date <= expiry_date
order_date <= expected_delivery_date
promotion_start_date <= promotion_end_date
sales_date <= forecast_as_of_date
```

### Prices

```text
unit_cost >= 0
selling_price >= 0
purchase_price >= 0
```

### Confidence and scores

```text
0.00 <= reliability_score <= 1.00
0.00 <= confidence <= 1.00
```

### Multi-tenancy

All joined entities must share the same `tenant_id`.

---

## 41. Data Quality Checks

Create a validation report containing:

```json
{
  "valid": true,
  "error_count": 0,
  "warning_count": 2,
  "row_counts": {
    "warehouses": 10,
    "retailers": 50,
    "skus": 100,
    "sales_history": 365000,
    "batch_inventory": 420
  },
  "warnings": [
    "2 SKUs have fewer than 30 days of sales history"
  ]
}
```

Recommended validations:

- Duplicate primary keys
- Missing required fields
- Invalid date formats
- Invalid enum values
- Orphan foreign keys
- Cross-tenant references
- Negative quantities
- Inconsistent PO balances
- Expired stock marked as available
- Impossible travel times
- Unrealistic price values
- Sales after the as-of date
- Excessive missing history

---

# Part VII — Model Training and Evaluation

## 42. Train, Validation and Test Split

Use time-based splitting, never random row splitting.

Example for one year of daily data:

```text
Training:   first 9 months
Validation: next 2 months
Test:       final 1 month
```

The test period must occur after the training period to imitate real forecasting.

---

## 43. Forecast Metrics

Calculate at least:

- MAE
- RMSE
- WAPE
- MAPE where valid
- Bias
- Stockout-risk recall

MAPE should not be used alone for intermittent or zero-demand products.

For the SIH prototype, display:

```text
Forecast error
Stockout-risk recall
Near-expiry detection rate
Expiry loss prevented
Working capital released
```

---

## 44. Scenario Evaluation

Compare system output with `expected_outcomes.csv`.

Example checks:

```text
Was the correct risk detected?
Was the correct source warehouse selected?
Was the destination warehouse appropriate?
Was the quantity within the accepted range?
Was safety stock protected?
Could the destination consume the batch before expiry?
Was net financial benefit positive?
Was human approval required?
```

---

# Part VIII — API and Import Guidance

## 45. MVP Import Sequence

Import files in this order:

```text
1. tenants.csv
2. warehouses.csv
3. retailers.csv
4. suppliers.csv
5. products.csv
6. skus.csv
7. supplier_sku.csv
8. lead_time_policies.csv
9. warehouse_routes.csv
10. promotions.csv
11. weather_daily.csv
12. local_events.csv
13. sales_history.csv
14. batch_inventory.csv
15. open_purchase_orders.csv
16. inventory_movements.csv
17. returns.csv
18. scenario_definitions.csv
```

Do not import `expected_outcomes.csv` into operational tables. Keep it under the test-fixture area.

---

## 46. CSV Import API Recommendation

```http
POST /api/v1/inventory/imports
Content-Type: multipart/form-data
```

Recommended request metadata:

```json
{
  "datasetVersion": "1.0.0",
  "tenantId": "TEN-ACME-PHARMA",
  "fileType": "BATCH_INVENTORY",
  "asOfDate": "2026-07-26",
  "replaceExisting": false
}
```

The backend must:

1. Store the raw file safely.
2. Validate schema and business rules.
3. Reject cross-tenant records.
4. Produce an import summary.
5. Preserve the import batch ID.
6. Record audit information.
7. Load valid data transactionally.

---

## 47. Database Mapping

| CSV file | PostgreSQL target |
|---|---|
| `warehouses.csv` | `warehouse` |
| `retailers.csv` | `retailer` |
| `products.csv` | `product` |
| `skus.csv` | `sku` |
| `sales_history.csv` | `sales_transaction` or daily demand fact |
| `batch_inventory.csv` | `batch_inventory` |
| `inventory_movements.csv` | `inventory_movement` |
| `open_purchase_orders.csv` | `open_purchase_order` |
| `promotions.csv` | `promotion` |
| `weather_daily.csv` | `weather_observation` |
| `local_events.csv` | `external_event` |

---

# Part IX — MCP Usage of Synthetic Data

## 48. Data MCP

The Data MCP should read synthetic data only through the backend APIs or authorised read model.

Example tools:

```text
get_inventory_summary
get_current_inventory
get_batch_inventory
find_near_expiry_inventory
find_excess_inventory
get_sales_history
get_open_purchase_orders
get_top_inventory_risks
```

The MCP server should not read arbitrary local CSV paths supplied by the user.

---

## 49. Intelligence MCP

The Intelligence MCP should use the same imported data for:

```text
forecast_demand
predict_stockout
calculate_safety_stock
recommend_stock_transfer
compare_purchase_vs_transfer
calculate_financial_benefit
explain_recommendation
```

Every result should include:

- `as_of` timestamp
- Tenant context
- Data version or snapshot ID
- Model version
- Confidence
- Evidence
- Assumptions
- Financial impact

---

## 50. Action MCP

Synthetic data may be used to demonstrate proposal creation.

Example flow:

```text
Recommendation generated
    ↓
User confirms
    ↓
Transfer proposal created
    ↓
Approval request created
    ↓
Proposal remains pending until approved
```

Even in a synthetic demo, the AI must not approve its own proposal or silently alter inventory.

---

# Part X — Security and Privacy

## 51. Synthetic Data Safety Rules

Synthetic data must not contain:

- Real patient names
- Real prescription records
- Real retailer owner details
- Personal mobile numbers
- Personal email addresses
- Bank details
- Real supplier contract prices
- Real access tokens
- Production tenant IDs
- Internal ERP credentials

Use invented names and clearly label the dataset as synthetic.

---

## 52. Tenant Isolation Test

Create at least one automated test that proves:

```text
TEN-ACME-PHARMA cannot access
TEN-FRESH-MART inventory, sales, recommendations or MCP resources.
```

Tenant IDs must come from trusted authentication context in production. They must not be accepted blindly from an MCP tool parameter.

---

# Part XI — Generator Configuration

## 53. `generator_config.yaml`

```yaml
dataset:
  name: stockflow-sih-demo-v1
  version: 1.0.0
  random_seed: 20260726
  currency: INR
  timezone: Asia/Kolkata
  history_start_date: 2025-08-01
  history_end_date: 2026-07-25
  forecast_as_of_date: 2026-07-26

scale:
  tenants: 3
  warehouses: 10
  retailers: 50
  skus: 100

vertical_distribution:
  PHARMA: 40
  SUPERMARKET: 35
  MERCHANDISE: 25

sales:
  minimum_history_days: 90
  maximum_history_days: 365
  stockout_probability: 0.04
  random_noise_stddev: 0.12

inventory:
  healthy_days_of_cover_min: 15
  healthy_days_of_cover_max: 45
  excess_days_of_cover_min: 60
  severe_excess_days_of_cover: 120

expiry:
  near_expiry_percentage: 0.08
  expired_percentage: 0.02

scenarios:
  - id: SCN-EXPIRY-001
    type: NEAR_EXPIRY
    sku_id: SKU-PARA-650
    source_warehouse_id: WH-CHENNAI
    destination_warehouse_ids:
      - WH-BENGALURU
      - WH-HYDERABAD

  - id: SCN-WEATHER-001
    type: WEATHER_LIFT
    category: BEVERAGE
    city: Chennai
    demand_multiplier: 1.35

  - id: SCN-DUPPO-001
    type: DUPLICATE_PO
    sku_id: SKU-AMOX-500
    warehouse_id: WH-BENGALURU
```

---

# Part XII — Definition of Done

## 54. Synthetic Dataset Completion Checklist

The dataset is ready when:

- [ ] The manifest is present.
- [ ] The random seed reproduces the same files.
- [ ] All CSV files use UTF-8 and standard headers.
- [ ] All dates use ISO format.
- [ ] All foreign keys are valid.
- [ ] No cross-tenant references exist.
- [ ] At least 90 days of history exists for normal SKUs.
- [ ] Fast-moving, seasonal, intermittent and slow-moving profiles exist.
- [ ] Stockout, expiry, excess and duplicate-PO scenarios exist.
- [ ] Ground-truth expected outcomes exist separately.
- [ ] Expired inventory is not considered usable.
- [ ] FEFO batches can be demonstrated.
- [ ] Route and transfer cost data exists.
- [ ] Financial-benefit calculation inputs exist.
- [ ] Data validation report passes.
- [ ] Data MCP can retrieve the data.
- [ ] Intelligence MCP can forecast and optimise using the data.
- [ ] Action MCP creates proposals without directly executing inventory changes.

---

## 55. Recommended First Implementation

Start with these eight files:

```text
warehouses.csv
retailers.csv
products.csv
skus.csv
sales_history.csv
batch_inventory.csv
open_purchase_orders.csv
warehouse_routes.csv
```

Then add:

```text
promotions.csv
weather_daily.csv
local_events.csv
inventory_movements.csv
returns.csv
scenario_definitions.csv
expected_outcomes.csv
```

This keeps the first implementation manageable while still supporting the complete SIH demonstration:

```text
Data → Forecast → Risk → Recommendation → Copilot → Proposal → Approval
```
