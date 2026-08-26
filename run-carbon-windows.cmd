@echo off
setlocal
cd /d "%~dp0services\carbon-service"

if not exist ".venv\Scripts\python.exe" (
  py -3.11 -m venv .venv || exit /b 1
)

call ".venv\Scripts\activate.bat" || exit /b 1
python -m pip install -r requirements.txt || exit /b 1
set "ALLOWED_ORIGINS=http://localhost:4200,http://127.0.0.1:4200"
python -m uvicorn stockflow_carbon.main:app --host 127.0.0.1 --port 8400
