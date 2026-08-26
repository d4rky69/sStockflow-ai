@echo off
setlocal
set "BASE_URL=http://localhost:8080"
set "TENANT_ID=TEN-ACME-PHARMA"

echo ============================================================
echo StockFlow AI Forecasting API Verification
echo ============================================================
echo.

echo 1. Creating focused seven-day forecast run...
curl -i -X POST ^
  -H "Content-Type: application/json" ^
  -H "X-Tenant-ID: %TENANT_ID%" ^
  -d "{\"asOfDate\":\"2026-07-26\",\"horizonDays\":7,\"historyDays\":180,\"warehouseId\":\"WH-CHENNAI\",\"skuId\":\"SKU-PARA-650\"}" ^
  "%BASE_URL%/api/v1/forecasts/runs"

echo.
echo.
echo 2. Reading latest forecast...
curl -i -H "X-Tenant-ID: %TENANT_ID%" ^
  "%BASE_URL%/api/v1/forecasts/latest?warehouseId=WH-CHENNAI&skuId=SKU-PARA-650"

echo.
echo.
echo 3. Reading model performance...
curl -i -H "X-Tenant-ID: %TENANT_ID%" ^
  "%BASE_URL%/api/v1/forecasts/model-performance"

echo.
echo.
echo 4. Reading forecast summary...
curl -i -H "X-Tenant-ID: %TENANT_ID%" ^
  "%BASE_URL%/api/v1/forecasts/summary"

echo.
pause
endlocal
