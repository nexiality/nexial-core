@echo off

setlocal enableextensions enabledelayedexpansion

call :init
if NOT ERRORLEVEL 0 goto :exit

call :title "nexial command generator"
if NOT ERRORLEVEL 0 goto :exit

call :checkJava
if NOT ERRORLEVEL 0 goto :exit

call :resolveEnv
if NOT ERRORLEVEL 0 goto :exit

%JAVA% -classpath %NEXIAL_CLASSES%;%NEXIAL_LIB%\* %JAVA_OPT% org.nexial.core.tools.CommandMetaGenerator -v

:init
	.commons.cmd %*

:checkJava
	.commons.cmd %*

:title
	.commons.cmd %*

:resolveEnv
	.commons.cmd %*

:exit
	endlocal
	exit /b 1