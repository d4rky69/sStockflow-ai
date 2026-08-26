@echo off
setlocal
cd /d "%~dp0services\copilot-service"
if not defined AUTH_DISABLED_FOR_LOCAL set AUTH_DISABLED_FOR_LOCAL=true
if not defined DEV_TENANT_ID set DEV_TENANT_ID=TEN-ACME-PHARMA
set "PYTHON_CMD=python"
where py >nul 2>nul
if not errorlevel 1 set "PYTHON_CMD=py -3"
%PYTHON_CMD% -m pip install -e .
if errorlevel 1 exit /b 1
%PYTHON_CMD% -m uvicorn stockflow_copilot.main:app --host 127.0.0.1 --port 8300
