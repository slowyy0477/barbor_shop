@echo off
setlocal
title Set the salon owner password
cd /d "%~dp0.."

echo ============================================================
echo   Set the salon OWNER password
echo ============================================================
echo.
echo This password opens the private Owner workspace in the app.
echo Customers never see that menu.
echo.
echo Use 10 to 20 characters with letters and numbers together,
echo for example:  salonowner2026pk
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0set-owner-password.ps1" -Restart
set "SALON_EXIT=%ERRORLEVEL%"

echo.
if not "%SALON_EXIT%"=="0" goto failed
echo Done. Open the app, tap the salon mark 5 times, tap Owner and
echo sign in with the owner mobile number and the password you typed.
echo.
pause
exit /b 0

:failed
echo The password was not changed. The reason is written above.
echo.
pause
exit /b %SALON_EXIT%
