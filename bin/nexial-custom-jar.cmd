@echo off

setlocal enableextensions enabledelayedexpansion

set NEXIAL_BIN=%~dp0

call :init
if NOT ERRORLEVEL 0 goto :exit

call :title "nexial custom library setup"
if NOT ERRORLEVEL 0 goto :exit

call :checkJava
if NOT ERRORLEVEL 0 goto :exit

call :resolveEnv
if NOT ERRORLEVEL 0 goto :exit

if "%1"=="" goto :reportBadInputAndExit

:createDir
	if not exist "%USER_NEXIAL_JAR%" (
	    echo.
		echo ^>^> create missing directory - %USER_NEXIAL_JAR%
		mkdir "%USER_NEXIAL_JAR%" 2>NUL
	)

:copyJars
    if "%~n1"=="" goto :eof

    echo.
    echo ^>^> following files/directories will be copied to %USER_NEXIAL_JAR%
    dir /b %1

    echo.
    set /p response="proceed with the copying (existing files will overwritten)? (Y/N) "

    if "%response%"=="N" goto :cancelCopy
    if "%response%"=="n" goto :cancelCopy
    if "%response%"=="y" goto :doCopy
    if "%response%"=="Y" goto :doCopy

:cancelCopy
     echo ^>^> copy cancelled...
     echo.
     shift
     goto :copyJars

:doCopy
    echo ^>^> copying from %1
    echo --------------------------------------------------------------------------------
    xcopy /S /Y /V /Z /F "%1" "%USER_NEXIAL_JAR%"
    echo --------------------------------------------------------------------------------
    echo.
    shift
    goto :copyJars

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
	echo USAGE: %0 [directory^|file]
	echo        %0 [directory^|file] [directory^|file] [...]
	echo.
	echo Files copied from the specified location to %USER_NEXIAL_JAR%
	echo will be used as additional libraries (jars) in your Nexial execution.
    echo.
	exit /b -1

goto :eof
