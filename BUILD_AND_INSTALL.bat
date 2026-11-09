@echo off
setlocal
title LVERN Nova - Build and Install
cd /d "%~dp0"
echo Building the current Nova source. For normal installation, use the release EXE.
call gradlew.bat build
if errorlevel 1 goto failed
py -3 scripts/package.py
if errorlevel 1 goto failed
"build\dist\Levrn.exe"
if errorlevel 1 goto failed
exit /b 0
:failed
echo Build or installation failed. No old executable was used as a fallback.
pause
exit /b 1
