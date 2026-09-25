@echo off
setlocal
title Stop Salon Server
cd /d "%~dp0.."

echo ============================================================
echo   Stopping your salon server
echo ============================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop-salon-server.ps1"
set "SALON_EXIT=%ERRORLEVEL%"

echo.
if not "%SALON_EXIT%"=="0" (
  echo The stop command reported a problem. Read the message above.
) else (
  echo Your salon server is now OFF. Start it again with start-salon-server.cmd
)
echo.
pause
exit /b %SALON_EXIT%
