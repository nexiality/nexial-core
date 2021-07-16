@echo off
setlocal enableextensions

REM ------------------------------------------------------------------------------------------------
REM Restart Android SDK's ADB to disconnect any previous or current device connections and reconnect
REM to any active connections. Run this batch file when the connected device (real or emulator) is
REM now registering with adb.
REM
REM For more details and options on ADB, check out
REM     https://developer.android.com/studio/command-line/adb#devicestatus
REM ------------------------------------------------------------------------------------------------

set ANDROID_SDK_ROOT=%USERPROFILE%\.nexial\android\sdk

cd /d %ANDROID_SDK_ROOT%\platform-tools
adb kill-server
adb start-server
adb devices -l

:end
endlocal
pause
