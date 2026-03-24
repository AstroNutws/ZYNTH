@echo off
setlocal

cd /d "%~dp0"

echo ====================================
echo Starting Zynth Schema Designer...
echo ====================================
echo.

mvn javafx:run
set EXIT_CODE=%ERRORLEVEL%

if not "%EXIT_CODE%"=="0" (
  echo.
  echo Zynth failed to start. Exit code: %EXIT_CODE%
  echo Make sure Java and Maven are installed and available in PATH.
  pause
)

endlocal
