@echo off
setlocal enableextensions

REM -----------------------------------------------------------------------------------------------
REM Copy local file to connected Android device
REM -----------------------------------------------------------------------------------------------

if "%2"=="" (
    echo ERROR: No source file or target folder specified!
    echo USAGE: %0 [source file] [target folder]
    echo.
    exit /b -1
)

set ANDROID_SDK_ROOT=%USERPROFILE%\.nexial\android\sdk
cd /d %ANDROID_SDK_ROOT%\platform-tools
adb push "%1" "/storage/self/primary/%2"
if %ERRORLEVEL% EQU 0 (
	exit /b 0
) else (
   echo ERROR: File copy from %1 to %2 failed: %errorlevel%
   exit /b %errorlevel%
)
