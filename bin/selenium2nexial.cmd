@echo off

REM --------------------------------------------------------------------------------
REM Make sure prerequisite environment variables are set
REM --------------------------------------------------------------------------------
if not "%JAVA_HOME%" == "" goto gotJdkHome
if not "%JRE_HOME%" == "" goto gotJreHome
echo Neither the JAVA_HOME nor the JRE_HOME environment variable is defined
echo At least one of these environment variable is needed to run this program
goto exit

:gotJdkHome
if not exist "%JAVA_HOME%\bin\java.exe" goto noJavaHome
echo Running %JAVA_HOME%\bin\java.exe
set JAVA_EXE="%JAVA_HOME%\bin\java.exe"
goto setClasspath

:gotJreHome
if not exist "%JRE_HOME%\bin\java.exe" goto noJavaHome
echo Running %JRE_HOME%\bin\java.exe
set JAVA_EXE="%JRE_HOME%\bin\java.exe"
goto setClasspath

:noJavaHome
echo The JAVA_HOME environment variable is not defined correctly
echo This environment variable is needed to run this program
echo NB: JAVA_HOME should point to a JDK not a JRE
goto exit


REM --------------------------------------------------------------------------------
REM setting Java runtime options and classpath
REM --------------------------------------------------------------------------------
:setClasspath
set NEXIAL_HOME=%~dp0..
set LIB_PATH=%NEXIAL_HOME%\lib
set CLASSES_PATH=%NEXIAL_HOME%\classes

REM Java RT requirement
set JAVA_OPT=-Xms256m -Xmx512m
set JAVA_OPT=%JAVA_OPT% -Dfile.encoding=UTF-8
set JAVA_OPT=%JAVA_OPT% -Dlog4j.configuration=nexial-log4j.xml


REM --------------------------------------------------------------------------------
REM run converter now
REM --------------------------------------------------------------------------------
echo.
%JAVA_EXE% -classpath "%LIB_PATH%\*" %JAVA_OPT% org.nexial.core.tools.SeleniumScriptConverter %*


:exit
endlocal
exit /b 1
