@echo off
REM --------------------------------------------------------------------------------
REM environment variable guide
REM --------------------------------------------------------------------------------
REM JAVA_HOME           - home directory of a valid JDK installation (1.6 or above)
REM PROJECT_HOME        - home directory of your project.
REM NEXIAL_OUT          - the output directory
REM FIREFOX_BIN         - the full path of firefox.exe
REM NEXIAL_RUNMODE      - determine screen capture image strategy (local or server)
REM --------------------------------------------------------------------------------

REM setlocal enableextensions enabledelayedexpansion
setlocal enableextensions

set NEXIAL_BIN=%~dp0

call :init
if NOT ERRORLEVEL 0 goto :exit

call :title "nexial runner"
if NOT ERRORLEVEL 0 goto :exit

call :checkJava
if NOT ERRORLEVEL 0 goto :exit

call :resolveEnv
if NOT ERRORLEVEL 0 goto :exit

REM setting project classpath (classes & lib)
REM giving priority to project-specific classpaths
set PROJECT_CLASSPATH=
if EXIST "%PROJECT_HOME%\nul" (
	set PROJECT_CLASSPATH="%PROJECT_HOME%\classes:%PROJECT_HOME%\lib\*"
)

REM setting additional Java runtime options and classpath

REM determine if nexial is running locally or on server (changes screencapture logic)
if "%NEXIAL_RUNMODE%"=="" set NEXIAL_RUNMODE=local
REM echo setting NEXIAL_RUNMODE as %NEXIAL_RUNMODE%
set JAVA_OPT=%JAVA_OPT% -Dnexial.scope.executionMode=%NEXIAL_RUNMODE%

REM determine the browser type to use for this run - THIS PROPERTY OVERWRITES THE SAME SETTING IN EXCEL
REM - valid types are: firefox, chrome, ie, safari
if NOT "%BROWSER_TYPE%"=="" (
REM	echo setting BROWSER_TYPE as %BROWSER_TYPE%
	set JAVA_OPT=%JAVA_OPT% -Dnexial.browser=%BROWSER_TYPE%
)

REM webdriver settings
set JAVA_OPT=%JAVA_OPT% -Dwebdriver.enable.native.events=true
set JAVA_OPT=%JAVA_OPT% -Dwebdriver.reap_profile=true
set JAVA_OPT=%JAVA_OPT% -Dwebdriver.accept.untrusted.certs=true
set JAVA_OPT=%JAVA_OPT% -Dwebdriver.assume.untrusted.issue=true

if [%CHROME_BIN%]==[] (
	if NOT [%DEFAULT_CHROME_BIN%]==[] (
		set CHROME_BIN=%DEFAULT_CHROME_BIN%
	)
)
if NOT [%CHROME_BIN%]==[] (
REM	echo setting CHROME_BIN as %CHROME_BIN%
	set JAVA_OPT=%JAVA_OPT% -Dwebdriver.chrome.bin=%CHROME_BIN%
)

if [%FIREFOX_BIN%]==[] (
	if NOT [%DEFAULT_FIREFOX_BIN%]==[] (
		set FIREFOX_BIN=%DEFAULT_FIREFOX_BIN%
	)
)
if NOT [%FIREFOX_BIN%]==[] (
REM	echo setting FIREFOX_BIN as %FIREFOX_BIN%
	set JAVA_OPT=%JAVA_OPT% -Dwebdriver.firefox.bin=%FIREFOX_BIN%
)

REM interactive mode support
if NOT "%NEXIAL_INTERACTIVE%"=="" set JAVA_OPT=$JAVA_OPT -Dnexial.interactive=%NEXIAL_INTERACTIVE%

REM run nexial now
echo.
%JAVA% -classpath %PROJECT_CLASSPATH%;%NEXIAL_CLASSES%;%NEXIAL_LIB%\nexial*.jar;%NEXIAL_LIB%\* %JAVA_OPT% org.nexial.core.Nexial %*
endlocal
exit /b 0
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
	goto :exit

:exit
	endlocal
	exit /b 1
