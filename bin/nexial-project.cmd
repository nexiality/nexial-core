@echo off

setlocal enableextensions enabledelayedexpansion

set NEXIAL_BIN=%~dp0

call :init
if NOT ERRORLEVEL 0 goto :exit

call :title "nexial project creator"
if NOT ERRORLEVEL 0 goto :exit

call :checkJava
if NOT ERRORLEVEL 0 goto :exit

call :resolveEnv
if NOT ERRORLEVEL 0 goto :exit

if "%1"=="" goto :reportBadInputAndExit

:createDir
	if exist %1 (
		set PROJECT_HOME=%1
	) else (
		set PROJECT_HOME=%PROJECT_BASE%\%1
	)
	echo   PROJECT_HOME:   %PROJECT_HOME%
	echo.

	echo creating project home at %PROJECT_HOME%
	mkdir %PROJECT_HOME%\artifact\script >NUL
	mkdir %PROJECT_HOME%\artifact\data >NUL
	mkdir %PROJECT_HOME%\artifact\\plan >NUL
	mkdir %PROJECT_HOME%\output >NUL

	set script_name=%~n1
	copy %NEXIAL_HOME%\template\nexial-testplan.xlsx %PROJECT_HOME%\artifact\plan\%script_name%-plan.xlsx >NUL

:copyTemplate
	echo create test script for %script_name%
	copy %NEXIAL_HOME%\template\nexial-script.xlsx %PROJECT_HOME%\artifact\script\%script_name%.xlsx >NUL
	copy %NEXIAL_HOME%\template\nexial-data.xlsx %PROJECT_HOME%\artifact\data\%script_name%.data.xlsx >NUL
	shift
	if "%1"=="" goto doneCopy
	set script_name=%~n1
	goto copyTemplate

:doneCopy
	cd %PROJECT_HOME%
	echo.
	echo DONE - nexial automation project created as follows:
	echo.

	cd %PROJECT_HOME%
	cd
	dir /s /b

	echo.
	echo.

	goto :eof

:init
	%NEXIAL_BIN%.commons.cmd %*

:checkJava
	%NEXIAL_BIN%.commons.cmd %*

:title
	%NEXIAL_BIN%.commons.cmd %*

:resolveEnv
	%NEXIAL_BIN%.commons.cmd %*

:reportBadInputAndExit
	echo.
	echo ERROR: Required input not found.
	echo USAGE: %0 [project name] [optional: testcase id, testcase id, ...]
	echo.
	echo.
	exit /b -1
	goto :eof

