@echo off
REM --------------------------------------------------------------------------------
REM environment variable guide
REM --------------------------------------------------------------------------------
REM JAVA_HOME           - home directory of a valid JDK installation (1.6 or above)
REM PROJECT_HOME        - home directory of your project.
REM NEXIAL_OUT          - the output directory
REM FIREFOX_BIN         - the full path of firefox.exe
REM NEXIAL_RUNMODE      - determine screen capture image strategy (local or server)
REM --------------------------------------------------------------------------------

setlocal enableextensions enabledelayedexpansion

set NEXIAL_BIN=%~dp0

call :init
if NOT ERRORLEVEL 0 goto :exit

call :title "nexial script updater"
if NOT ERRORLEVEL 0 goto :exit

call :checkJava
if NOT ERRORLEVEL 0 goto :exit

call :resolveEnv
if NOT ERRORLEVEL 0 goto :exit

REM run nexial now
REM echo Runtime Option: %JAVA_OPT%
echo.

REM run now
%JAVA% -classpath %NEXIAL_CLASSES%;%NEXIAL_LIB%\nexial*.jar;%NEXIAL_LIB%\* %JAVA_OPT% org.nexial.core.tools.LogFileParser %*

endlocal
exit /b 0
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
	endlocal
	exit /b 1




