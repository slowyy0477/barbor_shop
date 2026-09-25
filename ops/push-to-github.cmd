@echo off
setlocal
title Upload salon project to GitHub
cd /d "%~dp0.."

echo ============================================================
echo   Uploading your salon app to GitHub
echo ============================================================
echo.
echo The first time, Windows opens a GitHub sign-in window.
echo Sign in with the account  slowyy0477  and click Authorize.
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0push-to-github.ps1" %*
set "SALON_EXIT=%ERRORLEVEL%"

echo.
if not "%SALON_EXIT%"=="0" goto failed

echo ============================================================
echo   Done - your code is on GitHub.
echo ============================================================
echo.
echo Your repository: https://github.com/slowyy0477/barbor_shop
echo You can now download the APK from there on any phone.
echo.
pause
exit /b 0

:failed
echo ============================================================
echo   The upload did not finish.
echo ============================================================
echo.
echo Read the message above, then ask your helper.
echo Most common fix: run this file again and complete the GitHub
echo sign-in window without closing it.
echo.
pause
exit /b %SALON_EXIT%
