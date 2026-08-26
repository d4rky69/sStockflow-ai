@echo off
setlocal EnableExtensions

set "BASE_URL=http://localhost:8080"
set "TENANT_ID=TEN-ACME-PHARMA"
set "RESPONSE_FILE=%TEMP%\stockflow_forecast_5b_response.json"
set "STATUS_FILE=%TEMP%\stockflow_forecast_5b_status.txt"

echo ============================================================
echo StockFlow AI Increment 5B Forecast Quality Verification
echo ============================================================
echo.

echo 1. Checking backend health...
curl -sS "%BASE_URL%/actuator/health"
echo.
echo.

echo 2. Reading tenant forecast configuration...
curl -sS -i -H "X-Tenant-ID: %TENANT_ID%" "%BASE_URL%/api/v1/forecasts/configuration"
echo.
echo.

echo 3. Creating focused seven-day forecast run...
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
  del "%RESPONSE_FILE%" 2>nul
  del "%STATUS_FILE%" 2>nul
  exit /b 1
)

echo 4. Reading latest forecast with quality fields...
curl -sS -i -H "X-Tenant-ID: %TENANT_ID%" ^
  "%BASE_URL%/api/v1/forecasts/latest?warehouseId=WH-CHENNAI&skuId=SKU-PARA-650"
echo.
echo.

echo 5. Reading all candidate-model performance rows...
curl -sS -i -H "X-Tenant-ID: %TENANT_ID%" ^
  "%BASE_URL%/api/v1/forecasts/model-performance"
echo.
echo.

echo 6. Reading aggregate accuracy summary...
curl -sS -i -H "X-Tenant-ID: %TENANT_ID%" ^
  "%BASE_URL%/api/v1/forecasts/accuracy-summary"
echo.
echo.

echo Verification completed.
del "%RESPONSE_FILE%" 2>nul
del "%STATUS_FILE%" 2>nul
pause
endlocal
