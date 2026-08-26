@echo off
setlocal
cd /d "%~dp0"
call "%~dp0configure-maven-windows.cmd"
if errorlevel 1 (
  echo ERROR: Maven was not found.
  pause
  exit /b 1
)

if not defined STOCKFLOW_DB_URL set "STOCKFLOW_DB_URL=jdbc:postgresql://localhost:5432/stockflow"
if not defined STOCKFLOW_DB_USERNAME set "STOCKFLOW_DB_USERNAME=stockflow_app"
if not defined STOCKFLOW_DB_PASSWORD set "STOCKFLOW_DB_PASSWORD=stockflow_dev"

cd /d "%~dp0services\stockflow-core-api"

echo ============================================================
echo StockFlow AI Phase 2 Core API
echo ============================================================
echo Database: %STOCKFLOW_DB_URL%
echo Profile:  dev
echo.

echo Running tests with the isolated H2 test database...
call mvn -Dkotlin.compiler.daemon=false clean test
if errorlevel 1 (
  echo.
  echo ERROR: Backend tests failed. The API was not started.
  pause
  exit /b 1
)

echo.
echo Starting API with PostgreSQL and Flyway...
call mvn -Dkotlin.compiler.daemon=false -Dspring-boot.run.profiles=dev spring-boot:run
