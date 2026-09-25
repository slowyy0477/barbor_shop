@echo off
setlocal
title Show the newest sign-in code
cd /d "%~dp0.."

echo ============================================================
echo   Newest sign-in code for the customer at the counter
echo ============================================================
echo.
echo An SMS provider is not connected yet, so every code is also
echo written down on this laptop. Read the newest code below.
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0show-last-code.ps1"
set "SALON_EXIT=%ERRORLEVEL%"

echo.
echo Tip: the code expires in about five minutes and works once.
echo If it has already expired, ask the customer to tap "Send code"
echo again and then run this shortcut once more.
echo.
pause
exit /b %SALON_EXIT%
