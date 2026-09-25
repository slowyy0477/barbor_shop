@echo off
setlocal
title Salon Phone Link
cd /d "%~dp0.."

echo ============================================================
echo   Check / renew the salon phone link
echo ============================================================
echo.
echo This keeps the working link if it already works, or opens a
echo new free one. It can take up to one minute.
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0refresh-public-url.ps1"
echo.
echo Tip: if this says the link is unavailable, salon phones on the
echo same Wi-Fi can still use the "Same Wi-Fi" address shown by
echo Check Salon Server.
echo.
pause
endlocal
