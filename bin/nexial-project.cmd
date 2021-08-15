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
	rem project name might be specified as a directory
	echo "%1"|find ":\" > nul
	if "%errorlevel%"=="0" (
		rem project specified is a directory. let's make sure it's created
		mkdir "%1" > nul 2> nul
		if not "%errorlevel%"=="0" (
			echo ERROR: Unable to create specified project directory %1
			echo Check your input and try again
			echo.
			exit /b -1
			goto :eof
		)
	) else (
		echo %1 is not a directory, prepend it with %PROJECT_BASE%
	)

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
    if exist "%PROJECT_ID%" (
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

:copyProjectId
    REM create project.id file to uniquely identify a "project" across enterprise (i.e. same SCM)
    echo ^>^> (re)creating %PROJECT_ID% with %PROJECT_NAME%
    echo !PROJECT_NAME!> %PROJECT_ID%

:copyDefaultArtifacts
	REM create empty project.properties file if it does not exist
	if exist "%PROJECT_HOME%\artifact\project.properties" (
		echo ^>^> SKIPPED creating project.properties; already exists
	) else (
	    echo ^>^> create project.properties
		type NUL > "%PROJECT_HOME%\artifact\project.properties"
	)

    set plan_name=%~n1
    if exist "%PROJECT_HOME%\artifact\plan\*.xlsx" (
        echo ^>^> SKIPPED creating default plan file; already exists
    ) else (
        echo ^>^> create default plan file
        echo n | copy /-y "%NEXIAL_HOME%\template\nexial-testplan.xlsx" "%PROJECT_HOME%\artifact\plan\%plan_name%-plan.xlsx" >NUL
    )

	set script_name=%~n1
    if exist "%PROJECT_HOME%\artifact\script\*.xlsx" (
        echo ^>^> SKIPPED creating default script file; already exists
    ) else (
        echo ^>^> create default script file
        echo n | copy /-y "%NEXIAL_HOME%\template\nexial-script.xlsx" "%PROJECT_HOME%\artifact\script\%script_name%.xlsx" >NUL
    )

    if exist "%PROJECT_HOME%\artifact\data\*.xlsx" (
        echo ^>^> SKIPPED creating default data file; already exists
    ) else (
        echo ^>^> create default data file
        echo n | copy /-y "%NEXIAL_HOME%\template\nexial-data.xlsx" "%PROJECT_HOME%\artifact\data\%script_name%.data.xlsx" >NUL
    )

    if exist "%PROJECT_HOME%\artifact\script\%PROJECT_NAME%.macro.xlsx" (
	    echo ^>^> SKIPPED creating default macro file; already exists
    ) else (
	    echo ^>^> create default macro file
	    echo n | copy /-y "%NEXIAL_HOME%\template\nexial-macro.xlsx" "%PROJECT_HOME%\artifact\script\%PROJECT_NAME%.macro.xlsx" >NUL
	)

:copyBatchFiles
    if exist "%PROJECT_HOME%\artifact\bin\run-script.cmd" (
        echo ^>^> SKIPPED creating run-script.cmd; already exists
    ) else (
	    echo ^>^> create run-script.cmd
        "%NEXIAL_BIN%\windows\sed" -e s/%%PROJECT_NAME%%/%PROJECT_NAME%/g "%NEXIAL_HOME%\template\run-script.cmd.txt" > "%PROJECT_HOME%\artifact\bin\run-script.cmd"
    )
    if exist "%PROJECT_HOME%\artifact\bin\run-plan.cmd" (
        echo ^>^> SKIPPED creating run-plan.cmd; already exists
    ) else (
	    echo ^>^> create run-plan.cmd
        "%NEXIAL_BIN%\windows\sed" -e s/%%PROJECT_NAME%%/%PROJECT_NAME%/g "%NEXIAL_HOME%\template\run-plan.cmd.txt" > "%PROJECT_HOME%\artifact\bin\run-plan.cmd"
    )

:copyTemplate
    shift
	if "%~n1"=="" goto doneCopy
	set script_name=%~n1
    set macro_name=%script_name:~6%
	if "%script_name:~0,6%"=="macro:" (
        if exist "%PROJECT_HOME%\artifact\script\%macro_name%.xlsx" (
            echo ^>^> SKIPPED creating macro %macro_name%.xlsx; already exists
        ) else (
            echo ^>^> create macro %macro_name%.xlsx
            echo n | copy /-y "%NEXIAL_HOME%\template\nexial-macro.xlsx" "%PROJECT_HOME%\artifact\script\%macro_name%.xlsx" >NUL
        )
		goto copyTemplate
	) else (
        if exist "%PROJECT_HOME%\artifact\script\%script_name%.xlsx" (
            echo ^>^> SKIPPED creating script %script_name%.xlsx; already exists
        ) else (
            echo ^>^> create script %script_name%.xlsx
            echo n | copy /-y "%NEXIAL_HOME%\template\nexial-script.xlsx" "%PROJECT_HOME%\artifact\script\%script_name%.xlsx" >NUL
        )

        if exist "%PROJECT_HOME%\artifact\data\%script_name%.data.xlsx" (
            echo ^>^> SKIPPED creating data file %script_name%.data.xlsx; already exists
        ) else (
            echo ^>^> create data file %script_name%.data.xlsx
            echo n | copy /-y "%NEXIAL_HOME%\template\nexial-data.xlsx" "%PROJECT_HOME%\artifact\data\%script_name%.data.xlsx" >NUL
        )
		goto copyTemplate
	)

:doneCopy
	cd %PROJECT_HOME%
	echo ^>^> DONE - nexial automation project created as follows:
	echo.

	cd %PROJECT_HOME%
	cd
	dir /s /b /on *.xlsx *.properties *.cmd

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
	echo USAGE: %0 [project_name] [optional: script_name ... macro:macro_name ...]
	echo.
    echo  [project_name]        is required. Note that this can be just the name of the
    echo                        project - which will automatically be created under %PROJECT_BASE% -
    echo                        or, a PREEXISTING, fully qualified path.
    echo  [script_name]         is the script to create. Nexial will copy the script
    echo                        template to the specified project based on this name.
    echo                        A corresponding data file will also be created.
    echo                        Note: this is optional.
    echo  macro:[macro_name]    is the macro to create. Nexial will copy the macro
    echo                        template to the specified project based on this name.
    echo                        Note: this is optional.
    echo.
    echo  It is possible to specify multiple scripts and macros to create. For example,
    echo.
    echo        %0 my_project Script1 macro:CommonLib Script2 macro:Navigations ...
	echo.
	exit /b -1
	goto :eof
