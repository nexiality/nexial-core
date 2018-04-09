@echo off

setlocal enableextensions

set NEXIAL_BIN=%~dp0

call :init
if NOT ERRORLEVEL 0 goto :exit

call :title "nexial data variable updater"
if NOT ERRORLEVEL 0 goto :exit

call :checkJava
if NOT ERRORLEVEL 0 goto :exit

call :resolveEnv
if NOT ERRORLEVEL 0 goto :exit

echo.

%JAVA% -classpath %NEXIAL_LIB%\nexial*.jar;%NEXIAL_LIB%\* %JAVA_OPT% org.nexial.core.tools.DataVariableUpdater %*
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
