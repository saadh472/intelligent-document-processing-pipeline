@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy RemoteSigned -File "%~dp0launchers\start-local.ps1" %*
if not errorlevel 1 exit /b 0
echo.
echo The pipeline could not start. Review the error above.
if /i "%~1"=="-CheckOnly" exit /b 1
if /i "%~1"=="-NoBrowser" exit /b 1
pause
exit /b 1
