@echo off
REM Nexial Installer. v1.0

REM set up
echo.
echo --------------------------------------------------------------------------------
echo NEXIAL INSTALLER v1.0
echo --------------------------------------------------------------------------------

set PLATFORM=nexial-core
set PLATFORM_HOME=C:\projects\%PLATFORM%
set INSTALLER_PATH=%PLATFORM_HOME%-installing\
set INSTALLER_SOURCE=%PLATFORM_HOME%\bin\installer\windows

REM copy installer script to installer directory
REM gotta be pristine
IF EXIST %INSTALLER_PATH% (
	rmdir /s /q %INSTALLER_PATH% 1> nul
)

mkdir %INSTALLER_PATH%
copy /Y %INSTALLER_SOURCE%\*.* %INSTALLER_PATH% 1> nul

REM call installer script from installer directory (no wait, new window)
cd %PLATFORM_HOME%\..
start "installing nexial..." /D %INSTALLER_PATH% /B %INSTALLER_PATH%\_install.cmd %PLATFORM_HOME%

REM exit / good luck / see you on the other side
exit /b 0
