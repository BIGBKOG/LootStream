@echo off
setlocal
echo ========================================================================
echo   Building LootStreamHost-Setup.exe (All-in-One Windows Self-Installer)
echo ========================================================================

set "CSC=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if not exist "%CSC%" set "CSC=%WINDIR%\Microsoft.NET\Framework\v4.0.30319\csc.exe"

if not exist "%CSC%" (
    echo [ERROR] Could not find .NET Framework 4.x csc.exe on this PC.
    pause
    exit /b 1
)

"%CSC%" /nologo /target:winexe /optimize+ ^
  /out:"%~dp0LootStreamHost-Setup.exe" ^
  /reference:System.dll ^
  /reference:System.Core.dll ^
  /reference:System.Web.Extensions.dll ^
  /reference:System.Windows.Forms.dll ^
  /reference:System.Drawing.dll ^
  /resource:"%~dp0..\linux-host\web\index.html,index.html" ^
  /resource:"%~dp0..\linux-host\web\styles.css,styles.css" ^
  /resource:"%~dp0..\linux-host\web\app.js,app.js" ^
  "%~dp0LootStreamHost.cs"

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed.
    pause
    exit /b 1
)

echo.
echo [SUCCESS] Built single-file Windows installer: %~dp0LootStreamHost-Setup.exe
echo Distribute ONLY LootStreamHost-Setup.exe - users double-click it to install and run!
pause
