@echo off
setlocal
title Show the owner password
cd /d "%~dp0.."

echo ============================================================
echo   Owner sign-in details (keep this window private)
echo ============================================================

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0show-owner-password.ps1"
set "SALON_EXIT=%ERRORLEVEL%"

echo.
pause
exit /b %SALON_EXIT%
