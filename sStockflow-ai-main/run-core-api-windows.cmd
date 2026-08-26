@echo off
setlocal
cd /d "%~dp0"
call "%~dp0configure-maven-windows.cmd"
if errorlevel 1 (
  echo ============================================================
  echo StockFlow Core API
  echo ============================================================
  echo ERROR: Maven was not found.
  echo Expected either:
  echo   - mvn on PATH, or
  echo   - %%USERPROFILE%%\Tools\apache-maven-*\bin\mvn.cmd
  echo   - C:\Tools\apache-maven-*\bin\mvn.cmd
  pause
  exit /b 1
)

cd /d "%~dp0services\stockflow-core-api"

echo ============================================================
echo StockFlow Core API
echo ============================================================
echo.
echo Maven and Java used for this build:
call mvn --version
if errorlevel 1 (
  pause
  exit /b 1
)

echo.
echo Running tests...
call mvn -Dkotlin.compiler.daemon=false clean test
if errorlevel 1 (
  echo.
  echo ERROR: Backend tests failed. The API was not started.
  pause
  exit /b 1
)

echo.
echo Starting Spring Boot API on http://localhost:8080
call mvn -Dkotlin.compiler.daemon=false spring-boot:run
