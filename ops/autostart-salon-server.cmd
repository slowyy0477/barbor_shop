@echo off
rem Starts the salon server quietly at Windows sign-in.
rem No pause at the end: the window closes by itself after starting.
setlocal
title Salon Server (automatic)
cd /d "%~dp0.."

if not exist "%~dp0..\tmp" mkdir "%~dp0..\tmp"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-salon-server.ps1" >> "%~dp0..\tmp\salon-autostart.log" 2>&1
exit /b %ERRORLEVEL%
