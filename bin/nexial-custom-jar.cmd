@echo off

setlocal enableextensions enabledelayedexpansion

set NEXIAL_BIN=%~dp0

call :init
if NOT ERRORLEVEL 0 goto :exit

call :title "^copy nexial custom jars"
if NOT ERRORLEVEL 0 goto :exit

call :checkJava
if NOT ERRORLEVEL 0 goto :exit

call :resolveEnv
if NOT ERRORLEVEL 0 goto :exit

if "%1"=="" goto :reportBadInputAndExit

REM find %USER_HOME%\.nexial\jar
set NEXIAL_JAR="%homepath%\.nexial\jar"

:createDir
	if not exist "%NEXIAL_JAR%" (
		echo ^>^> Directory %NEXIAL_JAR% doesn't exist
		echo ^>^> Creating %NEXIAL_JAR% directory
		mkdir %NEXIAL_JAR% 2>NUL
	)else (
		echo ^>^> Skip creation of the directory %NEXIAL_JAR%
	)

:copyJars
    if "%~n1"=="" goto :eof
    echo /--------------------------------------------------------------------------------\
    echo ^>^> Following files/directory will be copied
    echo \--------------------------------------------------------------------------------/
    dir /b %1
    echo /--------------------------------------------------------------------------------\
    set /p response="Do you still want to continue copy (Y/N)?"
    echo \--------------------------------------------------------------------------------/

    if "%response%"=="N" goto :cancelCopy
    if "%response%"=="n" goto :cancelCopy
    if "%response%"=="y" goto :doCopy
    if "%response%"=="Y" goto :doCopy

:cancelCopy
     echo ^>^> ^Copy Cancelled..
     echo.
     shift
     goto :copyJars

:doCopy
    echo ^>^> copying custom jars/files/directories from "%1"
    echo --------------------------------------------------------------------------------
    xcopy /S /Y "%1" "%homepath%\.nexial\jar"
    echo --------------------------------------------------------------------------------
    echo ^>^> ^Copy Done..
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
	echo USAGE: %0 [source directory/file]
	echo.
	echo This command will copy files/directories from source location to ${NEXIAL_JAR}.
    echo You can also provide one or more source locations as like $0 [source directory/file] [source directory/file]
    echo.
	exit /b -1
	goto :eof
