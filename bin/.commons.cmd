@echo off
    setlocal enableextensions

    rem Not to be directly called
    exit /b 9009


:init
	REM # utilities to be invoked by other frontend scripts
	set PROJECT_BASE=%SystemDrive%\projects
	set NEXIAL_HOME=%~dp0..
	set NEXIAL_LIB=%NEXIAL_HOME%\lib
	set NEXIAL_CLASSES=%NEXIAL_HOME%\classes

	if exist "%LOCALAPPDATA%\Google\Chrome\Application\chrome.exe" (
		set DEFAULT_CHROME_BIN="%LOCALAPPDATA%\Google\Chrome\Application\chrome.exe"
	)
	if exist "%ProgramFiles%\Google\Chrome\Application\chrome.exe" (
		set DEFAULT_CHROME_BIN="%ProgramFiles%\Google\Chrome\Application\chrome.exe"
	)
	if exist "%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe" (
		set DEFAULT_CHROME_BIN="%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe"
	)

	if exist "%ProgramFiles%\Mozilla Firefox\firefox.exe" (
		set DEFAULT_FIREFOX_BIN="%ProgramFiles%\Mozilla Firefox\firefox.exe"
	)
	if exist "%ProgramFiles(x86)%\Mozilla Firefox\firefox.exe" (
		set DEFAULT_FIREFOX_BIN="%ProgramFiles(x86)%\Mozilla Firefox\firefox.exe"
	)

	REM # setting Java runtime options and classpath
	set JAVA_OPT=%JAVA_OPT% -ea
	set JAVA_OPT=%JAVA_OPT% -Xss24m
	set JAVA_OPT=%JAVA_OPT% -Dfile.encoding=UTF-8
	set JAVA_OPT=%JAVA_OPT% -Dnexial.home="%NEXIAL_HOME%"
	set JAVA_OPT=%JAVA_OPT% -Dwebdriver.winium.verbose=false
	set JAVA_OPT=%JAVA_OPT% -Dwebdriver.winium.silent=false
    REM set JAVA_OPT=%JAVA_OPT% -Dwebdriver.winium.logpath=%TEMP%\winium-service.log
	set JAVA_OPT=%JAVA_OPT% -Dorg.apache.poi.util.POILogger=org.apache.poi.util.NullLogger

	goto :eof


REM # Make sure prerequisite environment variables are set
:checkJava
	if "%JAVA_HOME%"=="" (
		if "%JRE_HOME%"=="" (
            echo =WARNING========================================================================
            echo Neither the JAVA_HOME nor the JRE_HOME environment variable is defined.
            echo Nexial will use the JVM based on current PATH. Nexial requires Java 1.8
            echo or above to run, so this might not work...
            echo ================================================================================
  		    echo.

            set JAVA=
            for /F "delims=" %%x in ('where java.exe') do (
                if [%JAVA%]==[] (
                    set JAVA="%%x"
                    goto :eof
                )
            )
		) else (
			if EXIST "%JRE_HOME%\bin\java.exe" (
				set JAVA="%JRE_HOME%\bin\java.exe"
			) else (
				echo ERROR!!!
				echo The JRE_HOME environment variable is not defined correctly.
				echo Unable to find "%JRE_HOME%\bin\java.exe"
				echo.
				exit /b -1
			)
		)
	) else (
		if EXIST "%JAVA_HOME%\bin\java.exe" (
			set JAVA="%JAVA_HOME%\bin\java.exe"
		) else (
			echo ERROR!!!
			echo The JAVA_HOME environment variable is not defined correctly.
			echo Unable to find "%JAVA_HOME%\bin\java.exe"
			echo.
			exit /b -1
		)
	)

	REM echo setting JAVA as %JAVA%
	goto :eof


:title
	echo.
	echo --------------------------------------------------------------------------------
	echo ^|                        nexial - test automation for all                      ^|
	echo --------------------------------------------------------------------------------
	echo [:: %~1 ::]
	echo --------------------------------------------------------------------------------
	goto :eof


:resolveEnv
	set NEXIAL_LIB=%NEXIAL_HOME%\lib
	set CLASSES_PATH=%NEXIAL_HOME%\classes
	set datestr=%date:~10,4%-%date:~4,2%-%date:~7,2% %time%

	echo ENVIRONMENT:
	echo   CURRENT TIME:   %datestr%
	echo   CURRENT USER:   %USERNAME%
	echo   CURRENT HOST:   %COMPUTERNAME%
	echo   JAVA:           %JAVA%
	echo   NEXIAL_HOME:    %NEXIAL_HOME%
	echo   NEXIAL_LIB:     %NEXIAL_LIB%
	echo   NEXIAL_CLASSES: %NEXIAL_CLASSES%
	echo   PROJECT_BASE:   %PROJECT_BASE%
	if NOT "%PROJECT_HOME%"=="" (
		echo   PROJECT_HOME:   %PROJECT_HOME%
	)
	echo.
	goto :eof


:testErrorlevel
    echo testErrorlevel
    exit /b 1
