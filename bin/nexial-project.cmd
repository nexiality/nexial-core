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

    rem determine project id
    rem first, use current project structure to determine project id
    set PROJECT_NAME=
    for %%F in (%1) do set PROJECT_NAME=%%~nF

    rem if project.id file exist, read its content as project id
    set PROJECT_ID=%PROJECT_HOME%\.meta\project.id
    if exist %PROJECT_ID% (
		for /f "delims=" %%x in (%PROJECT_ID%) do set PROJECT_NAME=%%x
	)

    rem lastly, user gets to choose
	set user_project_name=NUL
    echo.
    echo ^>^> currently project id is defined as %PROJECT_NAME%.
    echo ^>^> to change it, enter new project id below or press ENTER to keep it as is
    set /p user_project_name="  Enter project id [%PROJECT_NAME%]: "
    if not "%user_project_name%"=="NUL" (
        set PROJECT_NAME=%user_project_name%
    )

	echo.
	echo ^>^> (re)creating project home at %PROJECT_HOME%
	mkdir %PROJECT_HOME%\.meta 2>NUL
	mkdir %PROJECT_HOME%\artifact\bin 2>NUL
	mkdir %PROJECT_HOME%\artifact\script 2>NUL
	mkdir %PROJECT_HOME%\artifact\data 2>NUL
	mkdir %PROJECT_HOME%\artifact\plan 2>NUL
	mkdir %PROJECT_HOME%\output 2>NUL

    REM create project.id file to uniquely identify a "project" across enterprise (i.e. same SCM)
    echo ^>^> (re)creating %PROJECT_ID% with %PROJECT_NAME%"
    echo !PROJECT_NAME!> %PROJECT_ID%

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
	dir /s /b /on *.xlsx

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
