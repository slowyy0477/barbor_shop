@echo off
setlocal
title Connect real sign-in messages
cd /d "%~dp0.."

echo ============================================================
echo   Send sign-in codes to customer mobiles
echo ============================================================
echo.
echo Today the owner reads each code from this laptop. After this
echo setup the customer receives the code as a real message.
echo.
echo You need an account with one provider first:
echo   Twilio SMS          https://www.twilio.com/try-twilio
echo   WhatsApp Cloud API  https://business.facebook.com
echo   Your own gateway    ask your SMS company for its webhook
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0connect-sms.ps1"
set "SALON_EXIT=%ERRORLEVEL%"

echo.
if not "%SALON_EXIT%"=="0" (
  echo The provider setup did not finish. Nothing on the salon app was broken.
) else (
  echo Done. Sign-in codes now go to the customer mobile.
)
echo.
pause
exit /b %SALON_EXIT%
