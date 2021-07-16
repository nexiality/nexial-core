@echo off
setlocal enableextensions

REM ------------------------------------------------------------------------------------------------
REM Show android devices that are currently connected to this computer
REM
REM For more details and options on ADB, check out
REM     https://developer.android.com/studio/command-line/adb#devicestatus
REM ------------------------------------------------------------------------------------------------

set ANDROID_SDK_ROOT=%USERPROFILE%\.nexial\android\sdk

cd /d %ANDROID_SDK_ROOT%\platform-tools
adb devices -l

:end
endlocal
pause
