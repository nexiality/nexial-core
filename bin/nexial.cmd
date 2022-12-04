@echo off
REM --------------------------------------------------------------------------------
REM environment variable guide
REM --------------------------------------------------------------------------------
REM JAVA_HOME           - home directory of a valid JDK installation (1.8.0_152 or above)
REM PROJECT_HOME        - home directory of your project.
REM NEXIAL_OUTPUT       - the output directory (optional)
REM --------------------------------------------------------------------------------

setlocal enableextensions

set NEXIAL_RC=0
set NEXIAL_BIN=%~dp0

call :init
if NOT ERRORLEVEL 0 goto :exit

call :displayVersion %*
if %ERRORLEVEL% LEQ 0 goto :exit

call :title "nexial runner"
if NOT ERRORLEVEL 0 goto :exit

call :checkJava
if NOT ERRORLEVEL 0 goto :exit
if [%NEXIAL_RC%]==[3] goto :exit

call :resolveEnv
if NOT ERRORLEVEL 0 goto :exit

REM --------------------------------------------------------------------------------
REM setting project classpath (classes & lib)
REM --------------------------------------------------------------------------------
REM giving priority to project-specific classpaths
set PROJECT_CLASSPATH=
if EXIST "%PROJECT_HOME%\nul" (
    set PROJECT_CLASSPATH="%PROJECT_HOME%\classes:%PROJECT_HOME%\lib\*"
)

if [%CHROME_BIN%]==[] (
    if NOT [%DEFAULT_CHROME_BIN%]==[] (
        set CHROME_BIN=%DEFAULT_CHROME_BIN%
    )
)

if NOT [%CHROME_BIN%]==[] (
    REM echo setting CHROME_BIN as %CHROME_BIN%
    set JAVA_OPT=%JAVA_OPT% -Dwebdriver.chrome.bin=%CHROME_BIN%
)

if [%FIREFOX_BIN%]==[] (
    if NOT [%DEFAULT_FIREFOX_BIN%]==[] (
        set FIREFOX_BIN=%DEFAULT_FIREFOX_BIN%
    )
)

if NOT [%FIREFOX_BIN%]==[] (
    REM echo setting FIREFOX_BIN as %FIREFOX_BIN%
    set JAVA_OPT=%JAVA_OPT% -Dwebdriver.firefox.bin=%FIREFOX_BIN%
)

REM support JVM max mem config
if NOT [%NEXIAL_MAX_MEM%]==[] (
    set MAX_MEM=-Xmx%NEXIAL_MAX_MEM%
)

REM support environment default for output base directory
if NOT [%NEXIAL_OUTPUT%]==[] (
    set JAVA_OPT=%JAVA_OPT% -Dnexial.defaultOutBase=%NEXIAL_OUTPUT%
)

REM download nexial-lib-x.x.zip to userhome/.nexial/lib
CALL "%NEXIAL_HOME%\bin\nexial-lib-downloader.cmd"
set NEXIAL_RC=%ERRORLEVEL%
if NOT ERRORLEVEL 0 goto :exit

REM create keystore, if needed
REM if NOT [%USER_NEXIAL_KEYSTORE%]==[] (
REM     del /f /Q "%USER_NEXIAL_KEYSTORE%" 2>NUL
REM )

REM run nexial now
set runNexial=%JAVA% -classpath "%PROJECT_CLASSPATH%;%NEXIAL_CLASSES%;%NEXIAL_LIB%\nexial*.jar;%USER_NEXIAL_JAR%\*;%NEXIAL_LIB%\*;%USER_NEXIAL_LIB%\*" %MAX_MEM% %JAVA_OPT% -Djava.library.path="%USER_NEXIAL_DLL%" org.nexial.core.Nexial %*
echo.
%runNexial%
set NEXIAL_RC=%ERRORLEVEL%

set NEXIAL_INSTALLER_HOME="C:\projects\nexial-installer"
set UPDATE_AGREED="%USER_NEXIAL_INSTALL%\update-now"
if exist %UPDATE_AGREED% (
    set nexDirect=%cd%
    echo "Updating nexial-core..."
    DEL %UPDATE_AGREED%
    echo.
    %JAVA% -jar %NEXIAL_INSTALLER_HOME%\lib\nexial-installer.jar upgradeNexial
    cd /d %nexDirect%
)

set RESUME_FILE="%USER_NEXIAL_INSTALL%\resume-after-update"
if exist %RESUME_FILE% (
    echo "Resuming nexial-core execution."
    DEL %RESUME_FILE%
    echo.
    CALL "%NEXIAL_HOME%\bin\nexial.cmd" %*
    set NEXIAL_RC=%ERRORLEVEL%
)

goto :exit

:init
    %NEXIAL_BIN%.commons.cmd %*

:displayVersion
    if "%1"=="-version" (
        if "%2" == "" (
            type %NEXIAL_HOME%\version.txt
            echo.
            exit /b 0
        )
        echo ERROR: Argument "-version" cannot be used with any other argument.
        exit /b -1
    ) else (
        for %%x in (%*) do (
            if "%%x" == "-version" (
                echo ERROR: Argument "-version" cannot be used with any other argument.
                exit /b -1
            )
        )
    )
    exit /b 1

:checkJava
    %NEXIAL_BIN%.commons.cmd %*

:title
    %NEXIAL_BIN%.commons.cmd %*

:resolveEnv
    %NEXIAL_BIN%.commons.cmd %*

:exit
    REM TODO not sure if we need this - maybe yes, because we are using setlocal at the beginning
    endlocal
    exit /b %NEXIAL_RC%
