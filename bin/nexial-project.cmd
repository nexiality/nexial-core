@echo off

setlocal enableextensions enabledelayedexpansion

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
	set PROJECT_HOME=%PROJECT_BASE%\%1
	echo   PROJECT_HOME:   %PROJECT_HOME%
	echo.
	echo creating project home at %PROJECT_HOME%
	mkdir %PROJECT_HOME%\artifact\script >NUL
	mkdir %PROJECT_HOME%\artifact\data >NUL
	mkdir %PROJECT_HOME%\artifact\\plan >NUL
	mkdir %PROJECT_HOME%\output >NUL

	copy %NEXIAL_HOME%\template\nexial-testplan.xlsx %PROJECT_HOME%\artifact\plan\%1.xlsx >NUL

:copyTemplate
	echo create test script for %1
	copy %NEXIAL_HOME%\template\nexial-script.xlsx %PROJECT_HOME%\artifact\script\%1.xlsx >NUL
	copy %NEXIAL_HOME%\template\nexial-data.xlsx %PROJECT_HOME%\artifact\data\%1.data.xlsx >NUL
	shift
	if "%1"=="" goto doneCopy
	goto copyTemplate

:doneCopy
	nexial-script-update.cmd -v -t %PROJECT_HOME%

	cd %PROJECT_HOME%
	echo.
	echo DONE
	goto :eof

:init
	.commons.cmd %*

:checkJava
	.commons.cmd %*

:title
	.commons.cmd %*

:resolveEnv
	.commons.cmd %*

:reportBadInputAndExit
	echo.
	echo ERROR: Required input not found.
	echo USAGE: %0 [project name] [optional: testcase id, testcase id, ...]
	echo.
	echo.
	exit /b -1
	goto :eof
