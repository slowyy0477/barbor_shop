@echo off
setlocal
title Salon - STOP everything
cd /d "%~dp0"

echo ============================================================
echo    SALON APP  -  STOP EVERYTHING
echo ============================================================
echo.
echo  This turns OFF the salon server and the free phone link.
echo  Phones will NOT be able to book until you start it again
echo  with "1 START SALON.cmd".
echo.
echo  Your customers, bookings and wallet records are NOT deleted.
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0ops\stop-salon-server.ps1"
set "SALON_EXIT=%ERRORLEVEL%"

echo.
if not "%SALON_EXIT%"=="0" (
  echo  Something went wrong. Read the message above this line.
) else (
  echo ============================================================
  echo    DONE - everything is OFF.
  echo ============================================================
  echo.
  echo  You can close this window, or switch off the laptop.
)
echo.
pause
exit /b %SALON_EXIT%
