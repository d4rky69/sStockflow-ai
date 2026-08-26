@echo off
setlocal
cd /d "%~dp0"

echo ============================================================
echo StockFlow AI - Windows Launcher
echo ============================================================

echo Checking required commands...
call "%~dp0configure-maven-windows.cmd"
if errorlevel 1 (
  echo ERROR: Maven was not found on PATH or in a supported Tools folder.
  pause
  exit /b 1
)
for %%C in (node npm java python uv) do (
  where %%C >nul 2>nul
  if errorlevel 1 (
    echo ERROR: %%C is not available on PATH.
    echo Fix the installation, open a new Command Prompt, and run this file again.
    pause
    exit /b 1
  )
)

echo.
java -version
call mvn --version
python --version
call uv --version
node --version
call npm --version

echo.
echo Generating and validating synthetic data...
python scripts\generate_synthetic_data.py --config data\generator_config.yaml --output data\generated
if errorlevel 1 (
  pause
  exit /b 1
)
python scripts\validate_synthetic_data.py --dataset data\generated
if errorlevel 1 (
  pause
  exit /b 1
)

echo.
echo Opening service terminals...
start "StockFlow Core API" cmd /k call "%~dp0run-core-api-windows.cmd"
start "StockFlow Forecasting" cmd /k call "%~dp0run-forecasting-windows.cmd"
start "StockFlow Optimisation" cmd /k call "%~dp0run-optimisation-windows.cmd"
start "StockFlow MCP Data" cmd /k call "%~dp0run-mcp-data-windows.cmd"
start "StockFlow MCP Intelligence" cmd /k call "%~dp0run-mcp-intelligence-windows.cmd"
start "StockFlow MCP Action" cmd /k call "%~dp0run-mcp-action-windows.cmd"
timeout /t 8 /nobreak >nul
start "StockFlow Angular UI" cmd /k call "%~dp0run-web-windows.cmd"

echo.
echo Services are opening in separate terminals.
echo UI:           http://localhost:4200
echo Core API:     http://localhost:8080/api/v1/dashboard/overview
echo Health:       http://localhost:8080/actuator/health
echo Forecasting:  http://localhost:8101/docs
echo Optimisation: http://localhost:8102/docs
echo Data MCP:      http://127.0.0.1:8201/mcp
echo Intelligence:  http://127.0.0.1:8202/mcp
echo Action MCP:    http://127.0.0.1:8203/mcp
echo.
pause
