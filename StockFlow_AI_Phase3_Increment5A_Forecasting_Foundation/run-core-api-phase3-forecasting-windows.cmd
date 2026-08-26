@echo off
setlocal
call "%~dp0configure-maven-windows.cmd"
if errorlevel 1 (
  echo ERROR: Maven configuration failed.
  pause
  exit /b 1
)

if not defined STOCKFLOW_DB_URL set "STOCKFLOW_DB_URL=jdbc:postgresql://localhost:5433/stockflow_phase2"
if not defined STOCKFLOW_DB_USERNAME set "STOCKFLOW_DB_USERNAME=stockflow_app"
if not defined STOCKFLOW_DB_PASSWORD set "STOCKFLOW_DB_PASSWORD=stockflow_dev"

cd /d "%~dp0services\stockflow-core-api"

echo ============================================================
echo StockFlow AI Phase 3 Increment 5A - Forecasting Foundation
echo ============================================================
echo Database: %STOCKFLOW_DB_URL%
echo Profile:  phase2
echo.

echo Running backend tests with isolated H2...
call mvn -Dkotlin.compiler.daemon=false clean test
if errorlevel 1 (
  echo.
  echo ERROR: Backend tests failed. The API was not started.
  pause
  exit /b 1
)

echo.
echo Starting API on http://localhost:8080
echo Forecast endpoint: http://localhost:8080/api/v1/forecasts/runs
call mvn -Dkotlin.compiler.daemon=false -Dspring-boot.run.profiles=phase2 spring-boot:run
endlocal
