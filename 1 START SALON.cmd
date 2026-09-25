@echo off
setlocal
title Salon - START everything
cd /d "%~dp0"

echo ============================================================
echo    SALON APP  -  START EVERYTHING
echo ============================================================
echo.
echo  This turns ON:  1) salon database   2) salon server
echo                  3) free phone link   4) publishes the link
echo.
echo  Please wait. It can take 1 to 3 minutes. Do not close this
echo  window until you see the words "DONE".
echo.

rem -- Already on? Then do nothing unless the owner asks to restart --
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $r = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/actuator/health' -UseBasicParsing -TimeoutSec 6; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" 2>nul
if not "%ERRORLEVEL%"=="0" goto startnow

echo  Your salon server is ALREADY ON - nothing was changed.
echo.
echo  If you only wanted to start it, you are finished.
set "SALON_ANSWER="
set /p "SALON_ANSWER=  Type R and press Enter to restart it anyway: "
if /i "%SALON_ANSWER%"=="R" goto startnow
goto alreadyon

:startnow
echo.
echo  Starting everything now ...
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0ops\start-salon-server.ps1" %*
set "SALON_EXIT=%ERRORLEVEL%"
if not "%SALON_EXIT%"=="0" goto failed

echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0ops\status-salon-server.ps1"
echo.
echo ============================================================
echo    DONE - everything is ON and your phones can work.
echo ============================================================
echo.
echo  Copy the "Phone address" line above into a phone only if the
echo  app asks for the salon server address.
echo.
echo  To turn everything off later: double-click
echo  "2 STOP SALON.cmd"
echo.
pause
exit /b 0

:alreadyon
echo.
echo   DONE - nothing was changed. Everything is ON.
rem ping is used instead of timeout because timeout refuses redirected input.
ping -n 5 127.0.0.1 >nul
exit /b 0

:failed
echo.
echo ============================================================
echo    The start did NOT finish.
echo ============================================================
echo.
echo  The reason is written above this line. Take a photo of this
echo  window, or double-click "3 CHECK SALON.cmd" and send that
echo  picture to your helper.
echo.
pause
exit /b %SALON_EXIT%
