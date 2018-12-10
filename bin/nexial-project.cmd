@echo off

setlocal enableextensions enabledelayedexpansion

set NEXIAL_BIN=%~dp0

call :init
if NOT ERRORLEVEL 0 goto :exit

call :title "nexial project artifact creator"
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
	echo ^>^> (re)creating project home at %PROJECT_HOME%
	mkdir %PROJECT_HOME%\.meta 2>NUL
	mkdir %PROJECT_HOME%\artifact\script 2>NUL
	mkdir %PROJECT_HOME%\artifact\data 2>NUL
	mkdir %PROJECT_HOME%\artifact\plan 2>NUL
	mkdir %PROJECT_HOME%\output 2>NUL

    REM create project.id file to uniquely identify a "project" across enterprise (i.e. same SCM)
    set PROJECT_ID=%PROJECT_HOME%\.meta\project.id
    if not exist %PROJECT_ID% (
        echo ^>^> create %PROJECT_ID%
        echo %1 > %PROJECT_ID%
    )

	set script_name=%~n1
    set SKIP_DEF_SCRIPTS=false
    if exist %PROJECT_HOME%\artifact\script\*.xlsx (
        echo ^>^> skip over the creation of default script/data files
        set SKIP_DEF_SCRIPTS=true
        shift
    ) else (
        echo n | copy /-y %NEXIAL_HOME%\template\nexial-testplan.xlsx "%PROJECT_HOME%\artifact\plan\%script_name%-plan.xlsx" >NUL
    )

:copyTemplate
	if "%~n1"=="" goto doneCopy
	set script_name=%~n1
	echo ^>^> create test script and data file for '%script_name%'
	echo n | copy /-y %NEXIAL_HOME%\template\nexial-script.xlsx "%PROJECT_HOME%\artifact\script\%script_name%.xlsx" >NUL
	echo n | copy /-y %NEXIAL_HOME%\template\nexial-data.xlsx "%PROJECT_HOME%\artifact\data\%script_name%.data.xlsx" >NUL
	shift
	goto copyTemplate

:doneCopy
	cd %PROJECT_HOME%
	echo ^>^> DONE - nexial automation project created as follows:
	echo.

	cd %PROJECT_HOME%
	cd
	dir /s /b /on

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
