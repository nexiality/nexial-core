@echo off
setlocal enableextensions

REM -----------------------------------------------------------------------------------------------
REM Start Android Emulator using %1 as the emulator ID
REM
REM One can customize emulator behavior by setting the following environment variables prior to
REM invoking this script:
REM
REM     EMULATOR_TIMEZONE   - instruct Android emulator to use the specified timezone instead of
REM                           system default
REM     EMULATOR_MEMORY     - instruct Android emulator to use different RAM setting instead of
REM                           system default of 1536 MB
REM     EMULATOR_HEADLESS   - true|false to instruct Android emulator to run in desktop (default)
REM                           or headless mode.
REM     EMULATOR_FRONT_CAM  - emulated|webcam0|webcam1|...
REM                           instruct Android emulator to use either emulated image or host camera
REM     EMULATOR_BACK_CAM   - emulated|webcam0|webcam1|...
REM                           instruct Android emulator to use either emulated image or host camera
REM     EMULATOR_PHONE      - 10-digit; instruct Android emulator to use specified phone number
REM
REM For example,
REM
REM     set EMULATOR_TIMEZONE=America/Los_Angeles
REM     set EMULATOR_MEMORY=4096
REM     run-android-emulator.cmd Pixel_04a
REM
REM The above will instruct the Android Emulator to use Los Angeles as its timezone (i.e. PST)
REM and allocate 4 GB of RAM for the specified emulator.
REM
REM For more details and options on Android emulator, check out
REM     https://developer.android.com/studio/run/emulator-commandline
REM -----------------------------------------------------------------------------------------------

if "%1"=="" (
    echo "ERROR: No emulator id (avd id) specified!"
    echo.
    exit /b -1
)

set ANDROID_SDK_ROOT=%USERPROFILE%\.nexial\android\sdk
set EMULATOR_PHONE_NUMBER=2136394251
set EMU_OPTIONS=

if not "%EMULATOR_TIMEZONE%"=="" ( set EMU_OPTIONS=%EMU_OPTIONS% -timezone %EMULATOR_TIMEZONE%)
if not "%EMULATOR_MEMORY%"=="" ( set EMU_OPTIONS=%EMU_OPTIONS% -memory %EMULATOR_MEMORY%)
if not "%EMULATOR_HEADLESS%"=="" ( set EMU_OPTIONS=%EMU_OPTIONS% -no-window)
if not "%EMULATOR_FRONT_CAM%"=="" ( set EMU_OPTIONS=%EMU_OPTIONS% -camera-front %EMULATOR_FRONT_CAM%)
if not "%EMULATOR_BACK_CAM%"=="" ( set EMU_OPTIONS=%EMU_OPTIONS% -camera-back %EMULATOR_BACK_CAM%)
if not "%EMULATOR_PHONE%"=="" ( set EMULATOR_PHONE_NUMBER=%EMULATOR_PHONE%)
set EMU_OPTIONS=%EMU_OPTIONS% -phone-number %EMULATOR_PHONE_NUMBER%

cd /d "%ANDROID_SDK_ROOT%\emulator"
emulator -avd %1 -ranchu -allow-host-audio %EMU_OPTIONS%

:end
endlocal
