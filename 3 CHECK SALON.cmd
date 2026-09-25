@echo off
setlocal
title Salon - CHECK
cd /d "%~dp0"

echo ============================================================
echo    SALON APP  -  IS EVERYTHING ON?
echo ============================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0ops\status-salon-server.ps1"

echo.
echo  How to read the lines above:
echo    "ON"  = that part is working
echo    "OFF" = double-click "1 START SALON.cmd"
echo.
echo  "Same Wi-Fi" address works only on the salon Wi-Fi.
echo  "Phone address" works anywhere the phone has internet.
echo.
pause
endlocal
