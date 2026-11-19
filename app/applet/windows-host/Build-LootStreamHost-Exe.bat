@echo off
setlocal enabledelayedexpansion

echo ========================================================
echo   LootStream Windows PC Host - .EXE Builder
echo ========================================================
echo.

set "CSC_64=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
set "CSC_32=%WINDIR%\Microsoft.NET\Framework\v4.0.30319\csc.exe"
set "CSC="

if exist "%CSC_64%" (
    set "CSC=%CSC_64%"
) else if exist "%CSC_32%" (
    set "CSC=%CSC_32%"
)

if "%CSC%"=="" (
    echo [ERROR] Microsoft .NET Framework 4.x csc.exe was not found.
    echo You can alternatively run: python lootstream_host.py --gui
    echo Or build with PyInstaller: pyinstaller --onefile --windowed --name LootStreamHost lootstream_host.py
    pause
    exit /b 1
)

echo Using C# compiler: %CSC%
echo Compiling LootStreamHost.cs -> LootStreamHost.exe ...
echo.

"%CSC%" /nologo /target:winexe /optimize+ /out:"%~dp0LootStreamHost.exe" ^
    /reference:System.dll ^
    /reference:System.Core.dll ^
    /reference:System.Drawing.dll ^
    /reference:System.Windows.Forms.dll ^
    /reference:System.Web.Extensions.dll ^
    "%~dp0LootStreamHost.cs"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [FAILED] Compilation failed with error code %ERRORLEVEL%.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [SUCCESS] Built: %~dp0LootStreamHost.exe
echo Double-click LootStreamHost.exe to start hosting your media library!
echo.
pause
