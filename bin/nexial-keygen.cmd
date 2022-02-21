@echo off

REM ---------------------------------------------------------------------------
REM [nexial-keygen]
REM An use-only utility to generate nexial-only JKS cert for execution host.
REM Generated cert will be stored in %NEXIAL_USER_HOME%
REM ---------------------------------------------------------------------------

setlocal enableextensions enabledelayedexpansion

set NEXIAL_BIN=%~dp0

call :init
if NOT ERRORLEVEL 0 goto :exit

call :title "nexial certificate generator"
if NOT ERRORLEVEL 0 goto :exit

call :checkJava
if NOT ERRORLEVEL 0 goto :exit

call :resolveEnv
if NOT ERRORLEVEL 0 goto :exit

REM run now
call %NEXIAL_BIN%\.keygen.cmd
exit /b %ERRORLEVEL%
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
