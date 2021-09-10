@echo off
setlocal enableextensions

REM -----------------------------------------------------------------------------------------------
REM Update installed Android SDK found in %ANDROID_HOME%
REM -----------------------------------------------------------------------------------------------
set ANDROID_SDK_ROOT=%USERPROFILE%\.nexial\android\sdk

cd /d %ANDROID_SDK_ROOT%\cmdline-tools\latest\bin
sdkmanager.bat --update
