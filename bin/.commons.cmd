@echo off
    setlocal enableextensions enabledelayedexpansion

    rem Not to be directly called
    exit /b 9009


:init
	REM # utilities to be invoked by other frontend scripts
	set PROJECT_BASE=%SystemDrive%\projects
	set NEXIAL_HOME=%~dp0..
	set NEXIAL_LIB=%NEXIAL_HOME%\lib
	set NEXIAL_CLASSES=%NEXIAL_HOME%\classes
	set USER_HOME_NEXIAL_LIB=%USERPROFILE%\.nexial\lib

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

    REM android sdk
    if not exist "%ANDROID_HOME%\nul" ( set ANDROID_HOME=%USERPROFILE%\.nexial\android\sdk)
    if not exist "%ANDROID_SDK_ROOT%\nul" ( set ANDROID_SDK_ROOT=%USERPROFILE%\.nexial\android\sdk)

	REM javaui/jubula
    if not exist %JUBULA_HOME%\nul ( set JUBULA_HOME="%ProgramFiles%\jubula_8.8.0.034")

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

    set JAVA_VERSION=
    set JAVA_SUPPORTS_MODULE=true

    %JAVA% -version > %TEMP%\java_version 2>&1
    findstr /C:"\ \"16" %TEMP%\java_version
    if %ERRORLEVEL% EQU 0 ( set JAVA_VERSION=16)

    findstr /C:"\ \"15" %TEMP%\java_version
    if %ERRORLEVEL% EQU 0 ( set JAVA_VERSION=15)

    findstr /C:"\ \"14" %TEMP%\java_version
    if %ERRORLEVEL% EQU 0 ( set JAVA_VERSION=14)

    findstr /C:"\ \"13" %TEMP%\java_version
    if %ERRORLEVEL% EQU 0 ( set JAVA_VERSION=13)

    findstr /C:"\ \"12" %TEMP%\java_version
    if %ERRORLEVEL% EQU 0 ( set JAVA_VERSION=12)

    findstr /C:"\ \"11" %TEMP%\java_version
    if %ERRORLEVEL% EQU 0 ( set JAVA_VERSION=11)

    findstr /C:"\ \"10" %TEMP%\java_version
    if %ERRORLEVEL% EQU 0 ( set JAVA_VERSION=10)

    findstr /C:"\ \"1.9" %TEMP%\java_version
    if %ERRORLEVEL% EQU 0 (
        set JAVA_SUPPORTS_MODULE=false
        set JAVA_VERSION=1.9
    )

    findstr /C:"\ \"1.8" %TEMP%\java_version
    if %ERRORLEVEL% EQU 0 (
        set JAVA_SUPPORTS_MODULE=false
        set JAVA_VERSION=1.8
    )

    if "%JAVA_VERSION%"=="" (
        echo ERROR!!!
        echo Unknown or unsupported Java found:
        type %TEMP%\java_version
        echo.
        exit /b -2
    )

    del %TEMP%\java_version

    if "%JAVA_SUPPORTS_MODULE%"=="true" ( set JAVA_OPT=%JAVA_OPT% --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.text=ALL-UNNAMED --add-opens java.desktop/java.awt.font=ALL-UNNAMED)

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
	set datestr=%date% %time%

	echo ENVIRONMENT:
	echo   CURRENT TIME:   %datestr%
	echo   CURRENT USER:   %USERNAME%
	echo   CURRENT HOST:   %COMPUTERNAME%
	echo   JAVA:           %JAVA%
	echo   JAVA_VERSION:   %JAVA_VERSION%
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
