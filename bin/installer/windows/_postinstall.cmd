@echo off

set RC=0

REM make sure this script is called with expected args
IF "%~1"=="" goto bad_script

set INSTALLER_HOME=%~1
IF NOT EXIST %INSTALLER_HOME% GOTO bad_home

rmdir /S /Q %INSTALLER_HOME% 2> nul
echo [INFO] DONE!
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
