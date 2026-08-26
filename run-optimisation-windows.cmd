@echo off
setlocal
cd /d "%~dp0services\optimisation-service"
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
call uv run uvicorn stockflow_optimisation.main:app --port 8102
