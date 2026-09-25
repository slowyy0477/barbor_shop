@echo off
setlocal
title Ayan Salon Server
cd /d "%~dp0.."

echo ============================================================
echo   Starting your salon server on this laptop
echo ============================================================
echo.
echo This turns on the salon database and the salon server.
echo Please wait, it can take up to one minute the first time.
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-salon-server.ps1" %*
set "SALON_EXIT=%ERRORLEVEL%"

echo.
if not "%SALON_EXIT%"=="0" goto failed

echo ============================================================
echo   Done. Your salon server is ON.
echo ============================================================
echo.
echo Copy the "Phone address" line printed above into each salon phone:
echo   open the app - tap the salon mark 5 times - Owner - Settings -
echo   paste it in "Salon server address" - Save.
echo.
echo When the salon is closed for the day, run stop-salon-server.cmd
echo or simply shut down this laptop.
echo.
pause
exit /b 0

:failed
echo ============================================================
echo   The server did not start.
echo ============================================================
echo.
echo The reason is written above. Send a photo of this window
echo to your helper if you are not sure what it means.
echo.
pause
exit /b %SALON_EXIT%
