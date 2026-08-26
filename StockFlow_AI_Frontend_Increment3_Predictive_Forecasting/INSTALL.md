# StockFlow AI — Frontend Increment 3: Predictive Forecasting

This overlay connects the Angular **Demand Forecast** workspace to the Phase 3 Increment 5A forecasting APIs.

## Included functionality

- Run 7-day, 30-day, or 90-day forecasts
- Select warehouse and SKU scope
- Use 90, 180, or 365 days of history
- Automatically use the backend's latest available sales date
- Load the latest completed forecast
- View forecast-run history
- Display total forecast quantity
- Display average daily forecast
- Display current usable inventory
- Display projected stockout date
- Display selected forecasting model
- Display confidence classification
- Display prediction bounds
- Compare MAE, RMSE, MAPE, and bias across models
- Show model usage across the run
- Retain historical demand analytics as context
- Retain all prior mobile, dark-theme, topbar, hover, and spacing fixes

## Backend requirement

Keep the Phase 3 Increment 5A backend running on:

```text
http://localhost:8080
```

Verify:

```cmd
curl http://localhost:8080/actuator/health
```

The response must show:

```json
{"status":"UP"}
```

## Apply the overlay

Extract this ZIP.

Copy everything inside:

```text
StockFlow_AI_Frontend_Increment3_Predictive_Forecasting
```

into:

```text
C:\Users\oveyj\Downloads\StockFlow_AI_Phase2_Increment4
```

Choose:

```text
Replace the files in the destination
```

The ZIP uses the repository path:

```text
apps\stockflow-web
```

so Windows will merge it into the existing frontend.

## Files added

```text
apps\stockflow-web\src\app\core\models\forecast.models.ts
apps\stockflow-web\src\app\core\services\forecast-data.service.ts
```

## Files replaced

```text
apps\stockflow-web\proxy.conf.json
apps\stockflow-web\src\app\features\dashboard\dashboard.component.html
apps\stockflow-web\src\app\features\dashboard\dashboard.component.css
apps\stockflow-web\src\app\features\dashboard\dashboard.component.ts
```

## Run locally

Keep the backend terminal open.

Open a second CMD window:

```cmd
cd /d C:\Users\oveyj\Downloads\StockFlow_AI_Phase2_Increment4\apps\stockflow-web
npm install
npm start
```

Open:

```text
http://localhost:4200
```

Navigate to:

```text
Demand Forecast
```

The page should automatically load the latest completed forecast run. For the verified Acme Pharma run, it should show:

```text
Warehouse: Chennai Central Warehouse
SKU: Paracetamol 650mg Tablet
Model: Weighted Moving Average
Confidence: Low
Forecast quantity: approximately 418.94 units
Average daily forecast: approximately 59.85 units
Usable inventory: 3,100 units
Projected stockout: None
```

## Production build

After local verification:

```cmd
cd /d C:\Users\oveyj\Downloads\StockFlow_AI_Phase2_Increment4\apps\stockflow-web
npm run build
```

## Important deployment note

The deployed Cloudflare frontend calls the Google Cloud Run backend directly.

The new forecast page will work in production only after the Phase 3 Increment 5A backend has also been deployed to Cloud Run. Local testing works immediately because `proxy.conf.json` points to:

```text
http://localhost:8080
```

## Validation completed before packaging

- Forecast API contract matched against Increment 5A Kotlin DTOs and controller
- TypeScript strict compilation performed with Angular/RxJS interface stubs
- TypeScript compilation result: PASS
- CSS brace validation: PASS
- HTML structural tag counts: PASS
- Full Angular build must be run on the project machine because Angular 21.2 packages were unavailable in the packaging environment
