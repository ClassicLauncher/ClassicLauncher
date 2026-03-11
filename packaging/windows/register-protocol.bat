@echo off
::
:: register-protocol.bat — Manual classiclauncher:// URI scheme registration for
:: ZIP / portable installs of ClassicLauncher on Windows.
::
:: Run this script once after extracting the launcher. No administrator rights
:: are required because registration is written under HKCU (per-user).
::
:: The script auto-detects the launcher executable path from its own location.
:: Place this file in the same directory as ClassicLauncher.exe.
::

setlocal

set "EXE=%~dp0ClassicLauncher.exe"

if not exist "%EXE%" (
    echo ERROR: ClassicLauncher.exe not found at "%EXE%"
    echo Please place register-protocol.bat in the same directory as ClassicLauncher.exe.
    pause
    exit /b 1
)

echo Registering classiclauncher:// URI scheme for current user...

reg add "HKCU\Software\Classes\classiclauncher" /f /ve /t REG_SZ /d "URL:ClassicLauncher Protocol" >nul
reg add "HKCU\Software\Classes\classiclauncher" /f /v "URL Protocol" /t REG_SZ /d "" >nul
reg add "HKCU\Software\Classes\classiclauncher\DefaultIcon" /f /ve /t REG_SZ /d "\"%EXE%\",0" >nul
reg add "HKCU\Software\Classes\classiclauncher\shell\open\command" /f /ve /t REG_SZ /d "\"%EXE%\" \"%%1\"" >nul

if %errorlevel% neq 0 (
    echo ERROR: Registry write failed with code %errorlevel%.
    pause
    exit /b %errorlevel%
)

echo Done. You can now open classiclauncher:// links in your browser.
echo To unregister, delete HKCU\Software\Classes\classiclauncher in regedit.
pause
endlocal
