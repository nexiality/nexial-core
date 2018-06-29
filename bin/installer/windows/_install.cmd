@echo off

set RC=0
set INSTALLER_HOME=%~dp0

echo.
echo [INFO] installer found in %INSTALLER_HOME%

REM make sure this script is called with expected args
IF "%~1"=="" goto bad_script

set PLATFORM_HOME=%~1
IF NOT EXIST %PLATFORM_HOME% GOTO bad_home

set PLATFORM_BACKUP_HOME=%PLATFORM_HOME%.BAK

REM if backup directory already exists, wipe it
IF EXIST %PLATFORM_BACKUP_HOME%\nul (
	echo [INFO] remove existing backup in %PLATFORM_BACKUP_HOME%
	rmdir /s /q %PLATFORM_BACKUP_HOME% 1> nul
)

REM rename existing platform directory to backup
echo [INFO] moving %PLATFORM_HOME% to %PLATFORM_BACKUP_HOME%
move /Y %PLATFORM_HOME% %PLATFORM_BACKUP_HOME% 1> nul

REM now we are ready to upgrade
REM initial setup
%PLATFORM_HOME:~0,2%
mkdir %PLATFORM_HOME%
cd %PLATFORM_HOME%\..
del /s /q /f %PLATFORM_HOME%
rmdir /s /q %PLATFORM_HOME%

REM download latest distro
echo [INFO] download latest distro...
%INSTALLER_HOME%\wget -q --show-progress --content-disposition --no-check-certificate --directory-prefix=%PLATFORM_HOME%\.. "https://m74z8nduq3.execute-api.us-west-2.amazonaws.com/latest_sentry"

REM install distro
echo [INFO] unzip latest distro to %PLATFORM_HOME%...
%INSTALLER_HOME%\unzip -q -o -d %PLATFORM_HOME% %PLATFORM_HOME%\..\ep-nexial*.zip

REM version.txt to distro home
FOR /F "tokens=* USEBACKQ" %%F in (`dir /b %PLATFORM_HOME%\..\ep-nexial*.zip`) do (
	set ZIP_FILE=%%F
)
set DISTRO_FILE=%ZIP_FILE:~0,-4%
echo %DISTRO_FILE% > %PLATFORM_HOME%\version.txt

REM proofs
echo.
echo.
dir %PLATFORM_HOME%\lib\nexial*.jar
echo.
echo.

REM clean up
echo [INFO] remove distro zip
del /s /q %PLATFORM_HOME%\..\ep-nexial*.zip 1> nul

pause
cd %PLATFORM_HOME%\bin\installer\windows\
start "post-installation of nexial..." /D %PLATFORM_HOME%\bin\installer\windows %PLATFORM_HOME%\bin\installer\windows\_postinstall.cmd %INSTALLER_HOME%
exit


:bad_script
echo ERROR: expected argument(s) not specified!
set RC=-1
goto :end

:bad_home
echo ERROR: specified directory %~1 does not exist!
set RC=-2
goto :end

:end
exit /b %RC%
