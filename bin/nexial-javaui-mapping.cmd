@echo off

setlocal enableextensions

set NEXIAL_BIN=%~dp0

call :init
if NOT ERRORLEVEL 0 goto :exit

call :title "nexial java-ui mapping launcher"
if NOT ERRORLEVEL 0 goto :exit

call :checkJava
if NOT ERRORLEVEL 0 goto :exit

call :resolveEnv
if NOT ERRORLEVEL 0 goto :exit


REM download nexial-lib-x.x.zip to userhome/.nexial/lib
CALL "%NEXIAL_HOME%\bin\nexial-lib-downloader.cmd"
set NEXIAL_RC=%ERRORLEVEL%
if NOT ERRORLEVEL 0 goto :exit

echo.
echo.
%JAVA% -classpath "%NEXIAL_LIB%\nexial*.jar;%NEXIAL_LIB%\*;%USER_NEXIAL_LIB%\*" %JAVA_OPT% org.nexial.core.tools.JavaUIMapping %*
set RC=%ERRORLEVEL%
goto :eof

:init
	%NEXIAL_BIN%.commons.cmd %*

:checkJava
	%NEXIAL_BIN%.commons.cmd %*

:title
	%NEXIAL_BIN%.commons.cmd %*

:resolveEnv
	%NEXIAL_BIN%.commons.cmd %*

:exit
	exit /b %RC%
