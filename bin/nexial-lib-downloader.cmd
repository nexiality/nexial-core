@echo off

setlocal enabledelayedexpansion

set NEXIAL_BIN=%~dp0

call :init
if NOT ERRORLEVEL 0 goto :exit

REM download nexial-lib-x.x.zip to userhome/.nexial/lib
set libVersionFile=nexial-lib-version.txt
if exist %USER_NEXIAL_LIB%\%libVersionFile% (
    fc /lb1 "%USER_NEXIAL_LIB%\%libVersionFile%" "%NEXIAL_HOME%\lib\%libVersionFile%" > nul
    if [!ERRORLEVEL!]==[0] (goto :exit) else (goto :download)
) else (goto :download)


:download
    call :checkJava
    if NOT ERRORLEVEL 0 goto :exit
    %JAVA% -classpath %NEXIAL_CLASSES%;%NEXIAL_LIB%\nexial*.jar;%NEXIAL_LIB%\* %JAVA_OPT% org.nexial.core.tools.NexialLibDownloader %NEXIAL_HOME%
    goto :exit


:init
    %NEXIAL_BIN%.commons.cmd %*

:checkJava
    %NEXIAL_BIN%.commons.cmd %*

:exit
    exit /b %ERRORLEVEL%
    endlocal
