@echo off
setlocal EnableExtensions

set "BASE_URL=http://localhost:8080"
set "TENANT_ID=TEN-ACME-PHARMA"
set "RESPONSE_FILE=%TEMP%\stockflow_forecast_response.json"
set "STATUS_FILE=%TEMP%\stockflow_forecast_status.txt"

echo ============================================================
echo StockFlow AI Forecasting API Verification
echo ============================================================
echo.
echo Tenant       : %TENANT_ID%
echo Warehouse    : WH-CHENNAI
echo SKU          : SKU-PARA-650
echo Horizon      : 7 days
echo History      : 180 days
echo asOfDate     : Automatically uses latest available sales date
echo.

echo 1. Creating focused seven-day forecast run...
curl -sS -o "%RESPONSE_FILE%" -w "%%{http_code}" ^
  -X POST ^
  -H "Content-Type: application/json" ^
  -H "X-Tenant-ID: %TENANT_ID%" ^
  -d "{\"horizonDays\":7,\"historyDays\":180,\"warehouseId\":\"WH-CHENNAI\",\"skuId\":\"SKU-PARA-650\"}" ^
  "%BASE_URL%/api/v1/forecasts/runs" > "%STATUS_FILE%"

set "HTTP_STATUS="
set /p HTTP_STATUS=<"%STATUS_FILE%"

echo HTTP %HTTP_STATUS%
type "%RESPONSE_FILE%"
echo.
echo.

if not "%HTTP_STATUS%"=="201" (
  echo Forecast creation failed. Later verification calls were skipped.
  echo Review the response above.
  del "%RESPONSE_FILE%" 2>nul
  del "%STATUS_FILE%" 2>nul
  exit /b 1
)

echo 2. Reading latest forecast...
curl -sS -i ^
  -H "X-Tenant-ID: %TENANT_ID%" ^
  "%BASE_URL%/api/v1/forecasts/latest?warehouseId=WH-CHENNAI&skuId=SKU-PARA-650"

echo.
echo.
echo 3. Reading model performance...
curl -sS -i ^
  -H "X-Tenant-ID: %TENANT_ID%" ^
  "%BASE_URL%/api/v1/forecasts/model-performance"

echo.
echo.
echo 4. Reading forecast summary...
curl -sS -i ^
  -H "X-Tenant-ID: %TENANT_ID%" ^
  "%BASE_URL%/api/v1/forecasts/summary"

echo.
echo.
echo Verification completed.
del "%RESPONSE_FILE%" 2>nul
del "%STATUS_FILE%" 2>nul
pause
endlocal
