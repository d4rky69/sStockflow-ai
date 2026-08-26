@echo off
setlocal
cd /d "%~dp0apps\stockflow-web"
where npm >nul 2>nul
if errorlevel 1 (
  echo ERROR: npm is not available on PATH.
  pause
  exit /b 1
)
if not exist node_modules (
  call npm install
  if errorlevel 1 (
  pause
  exit /b 1
)
)
call npm start
