@echo off
setlocal
title Salon Server Check
cd /d "%~dp0.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0status-salon-server.ps1"
echo.
pause
endlocal
