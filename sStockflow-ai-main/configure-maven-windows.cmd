@echo off
rem Configure Maven from PATH or from common local installation folders.
where mvn >nul 2>nul
if not errorlevel 1 exit /b 0

for /f "delims=" %%D in ('dir /b /ad /o-n "%USERPROFILE%\Tools\apache-maven-*" 2^>nul') do (
  if exist "%USERPROFILE%\Tools\%%D\bin\mvn.cmd" (
    set "MAVEN_HOME=%USERPROFILE%\Tools\%%D"
    set "PATH=%USERPROFILE%\Tools\%%D\bin;%PATH%"
    exit /b 0
  )
)

for /f "delims=" %%D in ('dir /b /ad /o-n "C:\Tools\apache-maven-*" 2^>nul') do (
  if exist "C:\Tools\%%D\bin\mvn.cmd" (
    set "MAVEN_HOME=C:\Tools\%%D"
    set "PATH=C:\Tools\%%D\bin;%PATH%"
    exit /b 0
  )
)

exit /b 1
