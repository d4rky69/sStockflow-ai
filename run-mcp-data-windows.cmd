@echo off
setlocal
cd /d "%~dp0mcp"
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
call uv run python -m stockflow_mcp.data_server
