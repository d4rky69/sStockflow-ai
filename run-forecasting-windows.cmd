@echo off
setlocal
cd /d "%~dp0services\forecasting-service"
where uv >nul 2>nul
if errorlevel 1 (
  echo ERROR: uv is not available on PATH.
  pause
  exit /b 1
)
call uv sync
if errorlevel 1 (
  pause
  exit /b 1
)
call uv run uvicorn stockflow_forecasting.main:app --port 8101
