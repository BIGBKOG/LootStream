@echo off
setlocal
echo ============================================================
echo   Building LootStreamHost.exe (Windows Personal Media Host)
echo ============================================================

set "CSC=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if not exist "%CSC%" set "CSC=%WINDIR%\Microsoft.NET\Framework\v4.0.30319\csc.exe"

if not exist "%CSC%" (
    echo [ERROR] Could not find .NET Framework 4.x csc.exe on this PC.
    pause
    exit /b 1
)

"%CSC%" /nologo /target:winexe /optimize+ ^
  /out:"%~dp0LootStreamHost.exe" ^
  /reference:System.Web.Extensions.dll ^
  /reference:System.Windows.Forms.dll ^
  /reference:System.Drawing.dll ^
  "%~dp0LootStreamHost.cs"

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed.
    pause
    exit /b 1
)

echo.
echo [SUCCESS] Built: %~dp0LootStreamHost.exe
echo Double-click LootStreamHost.exe to start streaming to LootStream Android!
pause
