@echo off

setlocal enableextensions enabledelayedexpansion

set NEXIAL_BIN=%~dp0

call :init
if NOT ERRORLEVEL 0 goto :exit

call :title "nexial tms importer"
if NOT ERRORLEVEL 0 goto :exit

call :checkJava
if NOT ERRORLEVEL 0 goto :exit

call :resolveEnv
if NOT ERRORLEVEL 0 goto :exit

echo.

REM run now
%JAVA% -classpath "%PROJECT_CLASSPATH%;%NEXIAL_CLASSES%;%NEXIAL_LIB%\nexial*.jar;%USER_NEXIAL_JAR%\*;%NEXIAL_LIB%\*;%USER_NEXIAL_LIB%\*" %MAX_MEM% %JAVA_OPT% -Djava.library.path="%USER_NEXIAL_DLL%" org.nexial.core.tms.tools.TmsImporter %*

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

:exit
	endlocal
	exit /b 1
