@echo off
setlocal

cd /d "%~dp0"

where mvn >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Maven mvn was not found in PATH.
  echo Install Maven and make sure "mvn" works in a new terminal.
  pause
  exit /b 1
)

echo Starting Zynth Schema Designer...
call mvn javafx:run
set EXIT_CODE=%ERRORLEVEL%

if not "%EXIT_CODE%"=="0" (
  echo.
  echo [ERROR] App failed to start. Exit code: %EXIT_CODE%
  pause
)

exit /b %EXIT_CODE%
